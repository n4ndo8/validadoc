package br.com.mentoria.validadoc.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.tesseract.ETEXT_DESC;
import org.bytedeco.tesseract.TessBaseAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_GRAYSCALE;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_OTSU;
import static org.bytedeco.opencv.global.opencv_imgproc.threshold;
import static org.bytedeco.tesseract.global.tesseract.PSM_AUTO;

/**
 * Faz leitura real de PDFs e imagens; nenhuma informação é criada quando o OCR falha.
 * Bibliotecas nativas são carregadas apenas quando uma imagem precisa de OCR.
 */
@Service
public class DocumentTextExtractor {
    public static final int MAX_PAGES = 5;
    public static final long MAX_PIXELS = 20_000_000L;
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 60_000;
    private static final int OCR_DEADLINE_MILLIS = 25_000;
    private final Path tessdataDirectory;
    // Evita que uploads simultâneos esgotem a memória com várias imagens grandes.
    private final Semaphore processingSlot = new Semaphore(1);

    public DocumentTextExtractor(@Value("${app.ocr.tessdata:./.tools/tessdata}") String tessdata) {
        this.tessdataDirectory = Path.of(tessdata).toAbsolutePath().normalize();
    }

    public ExtractionResult extract(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Selecione um arquivo que não esteja vazio.");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("O arquivo deve ter no máximo 10 MB.");
        }
        boolean pdf = hasSignature(bytes, new int[]{0x25, 0x50, 0x44, 0x46, 0x2d});
        boolean png = hasSignature(bytes, new int[]{0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        boolean jpeg = hasSignature(bytes, new int[]{0xff, 0xd8, 0xff});
        if (!pdf && !png && !jpeg) {
            throw new IllegalArgumentException("Formato inválido. Envie um PDF, PNG ou JPG verdadeiro.");
        }
        if (!processingSlot.tryAcquire()) {
            throw new IllegalStateException("Outro documento está sendo processado. Aguarde alguns segundos e tente novamente.");
        }
        try {
            if (pdf) {
                return extractPdf(bytes);
            }
            verifyImageDimensions(bytes);
            List<String> warnings = new ArrayList<>();
            OcrText result = recognize(bytes, warnings);
            checkTextLimit(result.text());
            return new ExtractionResult(result.text(), "OpenCV + Tesseract (português)", warnings, result.confidence());
        } catch (LinkageError nativeError) {
            throw new IllegalStateException("Não foi possível carregar OpenCV/Tesseract. Execute o script de preparação e confira a plataforma Java.", nativeError);
        } finally {
            processingSlot.release();
        }
    }

    private ExtractionResult extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) {
                throw new IllegalArgumentException("Envie um PDF sem senha e com permissão para leitura do texto.");
            }
            int pages = document.getNumberOfPages();
            if (pages == 0 || pages > MAX_PAGES) {
                throw new IllegalArgumentException("O PDF deve conter de 1 a " + MAX_PAGES + " páginas.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            StringBuilder combinedText = new StringBuilder();
            List<String> warnings = new ArrayList<>();
            int ocrPages = 0;
            Integer minimumConfidence = null;
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);
                String text = cleanText(stripper.getText(document));
                // Cada página é decidida separadamente, inclusive em PDFs mistos.
                if (text.codePoints().filter(Character::isLetterOrDigit).count() < 60) {
                    float dpi = safeRenderDpi(document.getPage(pageIndex));
                    BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
                    try {
                        OcrText result = recognize(encodePng(image), warnings);
                        text = result.text();
                        minimumConfidence = minimumConfidence == null ? result.confidence()
                                : Math.min(minimumConfidence, result.confidence());
                    } finally {
                        image.flush();
                    }
                    ocrPages++;
                }
                if (text.isBlank()) {
                    warnings.add("A página " + (pageIndex + 1) + " não contém texto legível.");
                }
                if (!combinedText.isEmpty()) {
                    combinedText.append("\n\n");
                }
                combinedText.append(text);
                checkTextLimit(combinedText);
            }
            String method = ocrPages == 0 ? "PDFBox (texto do PDF)"
                    : ocrPages == pages ? "PDFBox + OpenCV + Tesseract (português)"
                    : "PDFBox + OpenCV + Tesseract (PDF misto)";
            return new ExtractionResult(combinedText.toString(), method, warnings, minimumConfidence);
        } catch (InvalidPasswordException exception) {
            throw new IllegalArgumentException("O PDF está protegido por senha. Envie uma cópia sem senha.", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível ler o PDF. Confira se o arquivo está íntegro.", exception);
        }
    }

    private OcrText recognize(byte[] bytes, List<String> warnings) {
        if (!Files.isRegularFile(tessdataDirectory.resolve("por.traineddata"))) {
            throw new IllegalStateException("O idioma português do Tesseract não está instalado. Execute preparar.ps1 antes de processar imagens.");
        }
        // Todos os Mat e ponteiros pertencentes a esta chamada são liberados ao final.
        try (BytePointer source = new BytePointer(bytes);
             Mat encoded = new Mat(1, bytes.length, CV_8UC1, source);
             Mat gray = imdecode(encoded, IMREAD_GRAYSCALE);
             Mat denoised = new Mat();
             Mat binary = new Mat();
             Size kernel = new Size(3, 3)) {
            if (gray.empty()) {
                throw new IllegalArgumentException("Não foi possível abrir a imagem. Envie um PNG ou JPG íntegro.");
            }
            if ((long) gray.cols() * gray.rows() > MAX_PIXELS) {
                throw new IllegalArgumentException("A imagem excede o limite de 20 milhões de pixels.");
            }
            GaussianBlur(gray, denoised, kernel, 0);
            threshold(denoised, binary, 0, 255, THRESH_BINARY | THRESH_OTSU);
            try (TessBaseAPI api = new TessBaseAPI(); ETEXT_DESC monitor = new ETEXT_DESC()) {
                try {
                    if (api.Init(tessdataDirectory.toString(), "por") != 0) {
                        throw new IllegalStateException("Não foi possível iniciar o Tesseract com o idioma português.");
                    }
                    api.SetPageSegMode(PSM_AUTO);
                    api.SetVariable("preserve_interword_spaces", "1");
                    // SetImage copia os bytes; binary permanece vivo durante todo o reconhecimento.
                    api.SetImage(binary.data(), binary.cols(), binary.rows(), 1, (int) binary.step());
                    api.SetSourceResolution(200);
                    monitor.set_deadline_msecs(OCR_DEADLINE_MILLIS);
                    if (api.Recognize(monitor) != 0) {
                        throw new IllegalStateException("O OCR não terminou no tempo disponível. Envie uma imagem menor e mais nítida.");
                    }
                    int confidence = api.MeanTextConf();
                    if (confidence < 60) {
                        warnings.add("O OCR apresentou baixa confiança. Confira os campos e envie uma imagem mais nítida se necessário.");
                    }
                    try (BytePointer output = api.GetUTF8Text()) {
                        String text = output == null || output.isNull() ? "" : cleanText(output.getString(StandardCharsets.UTF_8));
                        return new OcrText(text, confidence);
                    }
                } finally {
                    api.End();
                }
            }
        }
    }

    private static void verifyImageDimensions(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("A imagem não pôde ser lida. Confira se o arquivo está íntegro.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("A imagem deve ter no máximo 20 milhões de pixels.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("A imagem está danificada ou possui um formato inválido.", exception);
        }
    }

    private static float safeRenderDpi(PDPage page) {
        double width = page.getCropBox().getWidth();
        double height = page.getCropBox().getHeight();
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("O PDF contém uma página com dimensões inválidas.");
        }
        double dpi = Math.min(200, Math.sqrt(MAX_PIXELS * 0.9 / (width * height)) * 72);
        if (dpi < 36) {
            throw new IllegalArgumentException("O PDF possui uma página grande demais para processamento. Envie uma cópia em tamanho A4.");
        }
        return (float) dpi;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("Codificador PNG indisponível");
        }
        return output.toByteArray();
    }

    private static boolean hasSignature(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (Byte.toUnsignedInt(bytes[i]) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String cleanText(String text) {
        return text.replace('\u0000', ' ').replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static void checkTextLimit(CharSequence text) {
        if (text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("O documento contém texto demais para este formulário. Envie somente o comprovante.");
        }
    }

    private record OcrText(String text, int confidence) { }
}

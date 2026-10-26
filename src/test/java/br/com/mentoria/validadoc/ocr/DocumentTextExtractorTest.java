package br.com.mentoria.validadoc.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocumentTextExtractorTest {
    private final DocumentTextExtractor extractor = new DocumentTextExtractor(".tools/tessdata");

    @Test
    void rejectsFileDisguisedAsPdfBeforeLoadingNativeLibraries() {
        assertThatThrownBy(() -> extractor.extract("isto nao e um PDF".getBytes(StandardCharsets.UTF_8), "conta.pdf"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Formato inválido");
    }

    @Test
    void rejectsOversizedImageFromItsHeaderBeforeDecoding() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] bytes = output.toByteArray();
        ByteBuffer.wrap(bytes).putInt(16, 50_000).putInt(20, 50_000);
        CRC32 crc = new CRC32();
        crc.update(bytes, 12, 17);
        ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
        assertThatThrownBy(() -> extractor.extract(bytes, "enorme.png"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20 milhões");
    }

    @Test
    void readsTextPdfWithoutNeedingOcrLanguageFiles() throws Exception {
        DocumentTextExtractor withoutOcr = new DocumentTextExtractor("target/tessdata-inexistente");
        try (PDDocument pdf = new PDDocument()) {
            appendTextPage(pdf);
            ExtractionResult result = withoutOcr.extract(save(pdf), "comprovante.pdf");
            assertThat(result.text()).contains("Ana Silva", "Recife", "COMPROVANTE");
            assertThat(result.method()).isEqualTo("PDFBox (texto do PDF)");
            assertThat(result.ocrConfidence()).isNull();
        }
    }

    @Test
    void rejectsMoreThanFivePages() throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            for (int i = 0; i < 6; i++) {
                pdf.addPage(new PDPage());
            }
            assertThatThrownBy(() -> extractor.extract(save(pdf), "longo.pdf"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 a 5 páginas");
        }
    }

    @Test
    void readsRealPngThroughOpenCvAndTesseract() throws Exception {
        requirePortugueseModel();
        BufferedImage image = sampleImage();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            ExtractionResult result = extractor.extract(output.toByteArray(), "comprovante.png");
            assertThat(result.text()).containsIgnoringCase("Ana Silva").containsIgnoringCase("Recife");
            assertThat(result.method()).contains("OpenCV", "Tesseract");
            assertThat(result.ocrConfidence()).isGreaterThanOrEqualTo(60);
        } finally {
            image.flush();
        }
    }

    @Test
    void readsEveryPageOfMixedTextAndScannedPdf() throws Exception {
        requirePortugueseModel();
        BufferedImage image = sampleImage();
        try (PDDocument pdf = new PDDocument()) {
            appendTextPage(pdf);
            PDPage scannedPage = new PDPage();
            pdf.addPage(scannedPage);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, scannedPage)) {
                stream.drawImage(LosslessFactory.createFromImage(pdf, image), 20, 330, 570, 350);
            }
            ExtractionResult result = extractor.extract(save(pdf), "misto.pdf");
            assertThat(result.text()).contains("PAGINA TEXTUAL").containsIgnoringCase("Rua das Flores");
            assertThat(result.method()).contains("PDF misto");
            assertThat(result.ocrConfidence()).isGreaterThanOrEqualTo(60);
        } finally {
            image.flush();
        }
    }

    private static void requirePortugueseModel() {
        assumeTrue(Files.isRegularFile(Path.of(".tools/tessdata/por.traineddata")),
                "Execute preparar.ps1 para habilitar testes reais de OCR.");
    }

    private static byte[] save(PDDocument pdf) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        pdf.save(output);
        return output.toByteArray();
    }

    private static void appendTextPage(PDDocument pdf) throws Exception {
        PDPage page = new PDPage();
        pdf.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(40, 740);
            stream.setLeading(22);
            for (String line : new String[]{"PAGINA TEXTUAL - COMPROVANTE DE RESIDENCIA", "Nome: Ana Silva",
                    "CPF: 123.456.789-00", "Cidade: Recife", "Estado: PE", "Data de emissao: 26/10/2026"}) {
                stream.showText(line);
                stream.newLine();
            }
            stream.endText();
        }
    }

    private static BufferedImage sampleImage() {
        BufferedImage image = new BufferedImage(1800, 1100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 46));
            int y = 130;
            for (String line : new String[]{"COMPROVANTE DE RESIDENCIA", "Nome: Ana Silva",
                    "CPF: 123.456.789-00", "Endereco: Rua das Flores, 150", "Cidade: Recife", "Estado: PE",
                    "Data de emissao: 26/10/2026"}) {
                graphics.drawString(line, 100, y);
                y += 110;
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }
}

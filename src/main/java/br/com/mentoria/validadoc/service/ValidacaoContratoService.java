package br.com.mentoria.validadoc.service;

import br.com.mentoria.validadoc.api.ValidacaoRequest;
import br.com.mentoria.validadoc.api.ValidacaoResponse;
import br.com.mentoria.validadoc.api.ValidacaoResponse.*;
import br.com.mentoria.validadoc.domain.DadosDocumento;
import br.com.mentoria.validadoc.ocr.DocumentTextExtractor;
import br.com.mentoria.validadoc.ocr.ExtractionResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Adaptador do contrato didático. Não exige identidade nem altera o fluxo multipart. */
@Service
public class ValidacaoContratoService {
    private final DocumentTextExtractor ocr;
    private final FieldExtractionService fields;
    private final Clock clock;
    private final int maxAgeDays;

    public ValidacaoContratoService(DocumentTextExtractor ocr, FieldExtractionService fields, Clock clock,
            @Value("${app.validation.max-age-days:90}") int maxAgeDays) {
        this.ocr = ocr; this.fields = fields; this.clock = clock; this.maxAgeDays = maxAgeDays;
    }

    public ValidacaoRequest exemplo() {
        String date = LocalDate.now(clock).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"));
        return new ValidacaoRequest("comprovante-residencia.pdf", "application/pdf",
            "COMPROVANTE DE RESIDENCIA\nNome: Joao da Silva\nCPF: 123.456.789-00\n"
            + "Endereco: Rua das Flores, 150\nCidade: Guanambi - BA\nData de emissao: " + date, null);
    }

    public ValidacaoResponse validar(ValidacaoRequest input) {
        String mime = input.mimeType().strip().toLowerCase(Locale.ROOT);
        if (!Set.of("application/pdf", "image/png", "image/jpeg").contains(mime))
            throw new IllegalArgumentException("mimeType deve ser application/pdf, image/png ou image/jpeg.");
        boolean hasImage = input.imagemBase64() != null && !input.imagemBase64().isBlank();
        boolean hasText = input.conteudoDocumento() != null && !input.conteudoDocumento().isBlank();
        if (!hasImage && !hasText)
            throw new IllegalArgumentException("Envie conteudoDocumento ou imagemBase64.");
        List<Etapa> stages = new ArrayList<>();
        stages.add(new Etapa(1, "Documento", "Entrada do documento para o pipeline", input.nomeArquivo()));
        ExtractionResult extraction;
        if (hasImage) {
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(input.imagemBase64()); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("imagemBase64 inválida. Envie Base64 puro, sem prefixo data:."); }
            if (bytes.length > DocumentTextExtractor.MAX_FILE_BYTES)
                throw new IllegalArgumentException("O arquivo deve ter no máximo 10 MB.");
            verifyMime(bytes, mime);
            extraction = ocr.extract(bytes, input.nomeArquivo());
            boolean ranOcr = extraction.ocrConfidence() != null;
            stages.add(new Etapa(2, "OpenCV", "Pré-processamento das imagens",
                ranOcr ? "Escala de cinza, redução de ruído e binarização executadas."
                    : "Não executado: PDF com camada de texto lida pelo PDFBox."));
            stages.add(new Etapa(3, "Tesseract", "Leitura do documento",
                extraction.method() + (hasText ? ". O texto enviado foi ignorado: o arquivo tem prioridade." : "")
                    + (extraction.warnings().isEmpty() ? "" : ". " + String.join(" ", extraction.warnings()))));
        } else {
            extraction = new ExtractionResult(input.conteudoDocumento(), "Texto fornecido na requisição", List.of());
            stages.add(new Etapa(2, "OpenCV", "Pré-processamento das imagens", "Não executado: entrada somente textual."));
            stages.add(new Etapa(3, "Tesseract", "Leitura do documento", "Não executado: usado conteudoDocumento fornecido pelo cliente."));
        }
        if (extraction.text().length() > 50_000)
            throw new IllegalArgumentException("O texto do documento deve ter no máximo 50000 caracteres.");
        FieldExtractionResult interpreted = fields.extract(extraction.text());
        stages.add(new Etapa(4, "Interpretação", "Organização dos campos extraídos", interpreted.method()
            + (interpreted.warnings().isEmpty() ? "" : ". " + String.join(" ", interpreted.warnings()))));
        DadosDocumento data = interpreted.dados();
        List<String> passed = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        check(data.nome(), "Nome identificado", "Nome nao encontrado", passed, pending);
        check(data.cpf(), "CPF encontrado", "CPF nao encontrado", passed, pending);
        check(data.endereco(), "Endereco encontrado", "Endereco nao encontrado", passed, pending);
        check(data.cidade(), "Cidade encontrada", "Cidade nao encontrada", passed, pending);
        if (data.dataEmissao() == null) pending.add("Data de emissao nao encontrada");
        else {
            passed.add("Data de emissao reconhecida");
            LocalDate today = LocalDate.now(clock);
            if (data.dataEmissao().isAfter(today)) pending.add("Data de emissao no futuro");
            else if (data.dataEmissao().isBefore(today.minusDays(maxAgeDays))) pending.add("Emissao superior a " + maxAgeDays + " dias");
            else passed.add("Emissao dentro de " + maxAgeDays + " dias");
        }
        if (extraction.ocrConfidence() != null) {
            if (extraction.ocrConfidence() < 60) pending.add("Confianca do OCR inferior a 60%");
            else passed.add("Confianca do OCR de pelo menos 60%");
        }
        boolean approved = pending.isEmpty();
        Resultado result = new Resultado(approved, approved ? "APROVADO" : "REPROVADO", List.copyOf(passed), List.copyOf(pending));
        stages.add(new Etapa(5, "Java", "Aplicação das regras de negócio", result.status()));
        String city = data.cidade();
        if (city != null && data.estado() != null) city += " - " + data.estado();
        String cpf = data.cpf();
        if (cpf != null && cpf.matches("\\d{11}"))
            cpf = cpf.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        return new ValidacaoResponse(input.nomeArquivo(), List.copyOf(stages),
            new DadosExtraidos(data.nome(), cpf, data.endereco(), city, data.dataEmissao()), result);
    }

    private static void check(String value, String ok, String missing, List<String> passed, List<String> pending) {
        if (value == null || value.isBlank()) pending.add(missing); else passed.add(ok);
    }

    private static void verifyMime(byte[] bytes, String mime) {
        int[] signature = switch (mime) {
            case "image/png" -> new int[]{137, 80, 78, 71, 13, 10, 26, 10};
            case "image/jpeg" -> new int[]{255, 216, 255};
            default -> new int[]{37, 80, 68, 70, 45};
        };
        boolean matches = bytes.length >= signature.length;
        for (int i = 0; matches && i < signature.length; i++) matches = (bytes[i] & 255) == signature[i];
        if (!matches) throw new IllegalArgumentException("O conteúdo do arquivo não corresponde ao mimeType informado.");
    }
}

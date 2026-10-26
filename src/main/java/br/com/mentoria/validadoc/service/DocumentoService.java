package br.com.mentoria.validadoc.service;

import br.com.mentoria.validadoc.api.DocumentoResponse;
import br.com.mentoria.validadoc.domain.DocumentValidator;
import br.com.mentoria.validadoc.domain.RegraValidacao;
import br.com.mentoria.validadoc.ocr.DocumentTextExtractor;
import br.com.mentoria.validadoc.ocr.ExtractionResult;
import br.com.mentoria.validadoc.persistence.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentoService {
    private final DocumentTextExtractor ocr;
    private final FieldExtractionService fields;
    private final DocumentValidator validator;
    private final DocumentoRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DocumentoService(DocumentTextExtractor ocr, FieldExtractionService fields, DocumentValidator validator,
            DocumentoRepository repository, ObjectMapper mapper, Clock clock) {
        this.ocr = ocr; this.fields = fields; this.validator = validator;
        this.repository = repository; this.mapper = mapper; this.clock = clock;
    }

    public DocumentoResponse process(MultipartFile file, String expectedName) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Selecione um arquivo PDF, PNG ou JPG.");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("O arquivo deve ter no máximo 10 MB.");
        if (expectedName == null || expectedName.isBlank() || expectedName.strip().length() > 150)
            throw new IllegalArgumentException("Informe o nome completo esperado no comprovante (até 150 caracteres).");
        String filename = safeFilename(file.getOriginalFilename());
        ExtractionResult extracted = ocr.extract(file.getBytes(), filename);
        if (extracted.text().length() > 50_000)
            throw new IllegalArgumentException("O documento contém texto demais. Envie apenas as páginas do comprovante.");
        FieldExtractionResult result = fields.extract(extracted.text());
        List<RegraValidacao> rules = new ArrayList<>(validator.validate(result.dados(), extracted.text(), expectedName.strip()));
        if (extracted.ocrConfidence() != null) {
            int confidence = extracted.ocrConfidence();
            rules.add(new RegraValidacao("CONFIANCA_OCR", "Confiança do OCR de pelo menos 60%", confidence >= 60,
                "Menor confiança média entre as páginas lidas por OCR: " + confidence + "% (indicador de leitura, não de autenticidade)."));
        }
        List<String> warnings = new ArrayList<>(extracted.warnings());
        warnings.addAll(result.warnings());
        DocumentoResponse response = new DocumentoResponse(UUID.randomUUID(), filename, Instant.now(clock),
            rules.stream().allMatch(RegraValidacao::aprovada) ? "APROVADO" : "REPROVADO",
            extracted.method() + " → " + result.method(), extracted.text(), result.dados(), rules, warnings);
        repository.save(new DocumentoEntity(response.id(), response.criadoEm(), mapper.writeValueAsString(response)));
        return response;
    }

    public List<DocumentoResponse> list() { return repository.findTop100ByOrderByCriadoEmDesc().stream().map(this::decode).toList(); }
    public DocumentoResponse get(UUID id) {
        return repository.findById(id).map(this::decode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Envio não encontrado."));
    }
    private DocumentoResponse decode(DocumentoEntity entity) {
        try { return mapper.readValue(entity.getResultadoJson(), DocumentoResponse.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Não foi possível ler o resultado armazenado."); }
    }
    private String safeFilename(String original) {
        if (original == null || original.isBlank()) return "documento";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        return name.substring(0, Math.min(name.length(), 180));
    }
}

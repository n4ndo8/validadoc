package br.com.mentoria.validadoc.api;

import br.com.mentoria.validadoc.service.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DocumentoController {
    private final DocumentoService documents;
    private final FieldExtractionService fields;
    private final SampleDocumentService sample;
    private final int maxAgeDays;
    public DocumentoController(DocumentoService documents, FieldExtractionService fields, SampleDocumentService sample,
            @Value("${app.validation.max-age-days:90}") int maxAgeDays) {
        this.documents = documents; this.fields = fields; this.sample = sample; this.maxAgeDays = maxAgeDays;
    }
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("extractionMode", fields.mode(), "maxFileSizeMb", 10, "maxPages", 5, "maxAgeDays", maxAgeDays);
    }
    @PostMapping(value = "/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentoResponse> upload(@RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("nomeEsperado") String nomeEsperado) throws IOException {
        DocumentoResponse result = documents.process(arquivo, nomeEsperado);
        return ResponseEntity.created(java.net.URI.create("/api/documentos/" + result.id())).body(result);
    }
    @GetMapping("/documentos") public List<DocumentoResponse> list() { return documents.list(); }
    @GetMapping("/documentos/{id}") public DocumentoResponse get(@PathVariable UUID id) { return documents.get(id); }
    @GetMapping("/exemplo.pdf") public ResponseEntity<byte[]> example() throws IOException {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=comprovante-exemplo.pdf").body(sample.create());
    }
}

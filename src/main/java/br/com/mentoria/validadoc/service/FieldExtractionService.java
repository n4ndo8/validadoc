package br.com.mentoria.validadoc.service;

import br.com.mentoria.validadoc.domain.LocalFieldExtractor;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FieldExtractionService {
    private final LocalFieldExtractor local;
    private final OpenAiFieldExtractor openai;
    private final String mode;

    public FieldExtractionService(LocalFieldExtractor local, OpenAiFieldExtractor openai,
            @Value("${app.extraction-mode:LOCAL}") String mode) {
        this.local = local; this.openai = openai; this.mode = mode.toUpperCase(Locale.ROOT);
        if (!List.of("LOCAL", "OPENAI").contains(this.mode))
            throw new IllegalArgumentException("EXTRACTION_MODE deve ser LOCAL ou OPENAI.");
    }
    public String mode() { return mode; }
    public FieldExtractionResult extract(String text) {
        if (mode.equals("OPENAI")) return openai.extract(text);
        return new FieldExtractionResult(local.extract(text), "Extrator local por regras (sem IA)",
            List.of("O extrator local reconhece rótulos como Nome, CPF, Endereço, Cidade, Estado e Data de emissão. Layouts diferentes podem exigir conferência manual."));
    }
}

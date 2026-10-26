package br.com.mentoria.validadoc.ocr;

import java.util.List;

/** Texto efetivamente lido e procedência do processamento. */
public record ExtractionResult(String text, String method, List<String> warnings, Integer ocrConfidence) {
    public ExtractionResult {
        warnings = List.copyOf(warnings);
    }

    public ExtractionResult(String text, String method, List<String> warnings) {
        this(text, method, warnings, null);
    }
}

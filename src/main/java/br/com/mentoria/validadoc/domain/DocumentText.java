package br.com.mentoria.validadoc.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

final class DocumentText {
    static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO",
            "RR", "SC", "SP", "SE", "TO");

    private DocumentText() {
    }

    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    static String normalize(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return "";
        }
        return Normalizer.normalize(cleaned, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    static String evidenceText(String value) {
        return normalize(value).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    static boolean containsPhrase(String text, String value) {
        String phrase = evidenceText(value);
        return !phrase.isEmpty() && (" " + evidenceText(text) + " ").contains(" " + phrase + " ");
    }

    static boolean validState(String state) {
        return state != null && UFS.contains(state.strip().toUpperCase(Locale.ROOT));
    }
}

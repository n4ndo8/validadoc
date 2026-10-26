package br.com.mentoria.validadoc.domain;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrator determinístico para comprovantes com rótulos em português.
 * Não adivinha valores: rótulos conflitantes, datas inválidas e campos ausentes retornam null.
 */
@Component
public class LocalFieldExtractor {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2})(?!\\d)");
    private static final Pattern LABELED_CPF = Pattern.compile(
            "(?<![\\p{L}\\d])CPF(?:\\s*/\\s*CNPJ)?\\s*:?\\s*(\\d{3}[.\\s]?\\d{3}[.\\s]?\\d{3}[-\\s]?\\d{2})(?!\\d)", FLAGS);
    private static final Pattern FORMATTED_CPF = Pattern.compile(
            "(?<![\\d.])\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}(?![\\d/])");
    private static final Pattern CITY_STATE = Pattern.compile(
            "^([\\p{L}][\\p{L} .’'-]{1,79}?)\\s*[-/]\\s*([A-Za-z]{2})$");
    private static final String NAME_LABEL = "nome(?:\\s+(?:completo|do\\s+titular))?|titular|cliente|consumidor";
    private static final String ADDRESS_LABEL = "endere[cç]o(?:\\s+(?:residencial|do\\s+im[oó]vel|de\\s+instala[cç][aã]o))?|logradouro";
    private static final String CITY_LABEL = "cidade|munic[ií]pio";
    private static final String STATE_LABEL = "estado|uf";
    private static final String CITY_STATE_LABEL = "(?:cidade|munic[ií]pio)\\s*/\\s*(?:uf|estado)";
    private static final String DATE_LABEL = "data\\s*(?:d[ae]\\s*)?emiss[aã]o|emiss[aã]o|emitido\\s+em";
    private static final Set<String> MISSING_VALUES = Set.of("-", "n/a", "null", "nao informado",
            "nao informada", "nao consta", "nao encontrado", "nao encontrada", "ilegivel", "sem informacao");
    private static final Pattern INLINE_LABEL = Pattern.compile(
            "[\\t ]+(?=(?:" + NAME_LABEL + "|CPF|" + ADDRESS_LABEL + "|" + CITY_STATE_LABEL
                    + "|" + CITY_LABEL + "|" + STATE_LABEL + "|" + DATE_LABEL + ")\\s*[:=])", FLAGS);

    public DadosDocumento extract(String text) {
        if (text == null || text.isBlank()) {
            return new DadosDocumento(null, null, null, null, null, null);
        }
        List<String> lines = lines(text);
        String nome = unique(labeledValues(lines, NAME_LABEL));
        String endereco = unique(labeledValues(lines, ADDRESS_LABEL));

        List<String> cities = new ArrayList<>();
        List<String> states = new ArrayList<>(labeledValues(lines, STATE_LABEL));
        List<String> combined = new ArrayList<>(labeledValues(lines, CITY_STATE_LABEL));
        for (String city : labeledValues(lines, CITY_LABEL)) {
            if (CITY_STATE.matcher(city).matches()) {
                combined.add(city);
            } else {
                cities.add(city);
            }
        }
        combined.addAll(lines);
        for (String candidate : combined) {
            Matcher matcher = CITY_STATE.matcher(candidate.strip());
            if (matcher.matches() && DocumentText.validState(matcher.group(2))) {
                cities.add(matcher.group(1));
                states.add(matcher.group(2));
            }
        }
        String cidade = unique(cities);
        String estado = unique(states);
        estado = DocumentText.validState(estado) ? estado.toUpperCase(Locale.ROOT) : null;

        return new DadosDocumento(nome, cpf(text), endereco, cidade, estado,
                emissionDate(labeledValues(lines, DATE_LABEL)));
    }

    private List<String> lines(String text) {
        String separated = text.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ')
                .replaceAll("[\\t ]*[|;][\\t ]*", "\n");
        separated = INLINE_LABEL.matcher(separated).replaceAll("\n");
        return separated.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    private List<String> labeledValues(List<String> lines, String labels) {
        Pattern pattern = Pattern.compile("^\\s*(?:" + labels + ")\\s*(?::|=)\\s*(.*?)\\s*$", FLAGS);
        List<String> values = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches() && DocumentText.clean(matcher.group(1)) != null) {
                values.add(matcher.group(1));
            }
        }
        return values;
    }

    private String cpf(String text) {
        List<String> candidates = new ArrayList<>();
        Matcher labeled = LABELED_CPF.matcher(text);
        while (labeled.find()) {
            candidates.add(labeled.group(1).replaceAll("\\D", ""));
        }
        Matcher formatted = FORMATTED_CPF.matcher(text);
        while (formatted.find()) {
            candidates.add(formatted.group().replaceAll("\\D", ""));
        }
        return unique(candidates);
    }

    private LocalDate emissionDate(List<String> values) {
        LocalDate result = null;
        for (String value : values) {
            Matcher matcher = DATE.matcher(value);
            boolean found = false;
            while (matcher.find()) {
                found = true;
                LocalDate candidate;
                try {
                    String date = matcher.group(1);
                    candidate = date.contains("/") ? LocalDate.parse(date, BR_DATE) : LocalDate.parse(date);
                } catch (DateTimeParseException ex) {
                    return null;
                }
                if (result != null && !result.equals(candidate)) {
                    return null;
                }
                result = candidate;
            }
            if (!found) {
                return null;
            }
        }
        return result;
    }

    private String unique(List<String> values) {
        Map<String, String> candidates = new LinkedHashMap<>();
        for (String value : values) {
            String cleaned = DocumentText.clean(value);
            if (cleaned != null && !MISSING_VALUES.contains(DocumentText.normalize(cleaned))) {
                candidates.putIfAbsent(DocumentText.normalize(cleaned), cleaned);
            }
        }
        return candidates.size() == 1 ? candidates.values().iterator().next() : null;
    }
}

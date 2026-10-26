package br.com.mentoria.validadoc.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class DocumentValidator {
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final Pattern RESIDENCE_HINT = Pattern.compile(
            "\\b(?:comprovante de (?:residencia|endereco)|"
                    + "(?:conta|fatura) (?:de|da|do) (?:agua|luz|energia|telefone|telefonia|internet|cartao)|"
                    + "energia eletrica|consumo de (?:agua|energia)|abastecimento de agua)\\b");
    private final Clock clock;
    private final int maxAgeDays;

    public DocumentValidator(Clock clock, @Value("${app.validation.max-age-days:90}") int maxAgeDays) {
        if (maxAgeDays < 0) {
            throw new IllegalArgumentException("A idade máxima do documento não pode ser negativa.");
        }
        this.clock = clock;
        this.maxAgeDays = maxAgeDays;
    }

    public List<RegraValidacao> validate(DadosDocumento dados, String texto, String nomeEsperado) {
        DadosDocumento document = dados == null
                ? new DadosDocumento(null, null, null, null, null, null) : dados;
        List<RegraValidacao> rules = new ArrayList<>();
        boolean recognizedType = RESIDENCE_HINT.matcher(DocumentText.evidenceText(texto)).find();
        rules.add(new RegraValidacao("TIPO_DOCUMENTO", "Tipo de comprovante reconhecido", recognizedType,
                recognizedType ? "O texto contém indicação de comprovante de residência ou de uma conta aceita."
                        : "Não foi encontrada indicação de comprovante de residência, água, energia, telefone, internet ou cartão."));

        boolean namePresent = validPersonName(document.nome());
        rules.add(new RegraValidacao("NOME", "Nome do titular encontrado", namePresent,
                namePresent ? "Nome presente e com formato legível." : "Nome ausente ou com formato inconsistente."));

        boolean cpfPresent = document.cpf() != null && document.cpf().matches("\\d{11}");
        rules.add(new RegraValidacao("CPF", "CPF com 11 dígitos encontrado", cpfPresent,
                cpfPresent ? "CPF encontrado; esta regra não verifica os dígitos verificadores."
                        : "Não foi possível extrair um CPF completo com 11 dígitos."));

        boolean addressPresent = validAddress(document.endereco()) && validCity(document.cidade())
                && DocumentText.validState(document.estado());
        rules.add(new RegraValidacao("ENDERECO", "Endereço, cidade e UF encontrados", addressPresent,
                addressPresent ? "Endereço, cidade e UF presentes."
                        : "Endereço e cidade precisam estar legíveis, com uma UF brasileira válida."));

        LocalDate today = LocalDate.now(clock);
        LocalDate issued = document.dataEmissao();
        boolean dateValid = issued != null && !issued.isAfter(today) && !issued.isBefore(today.minusDays(maxAgeDays));
        String dateDetail = issued == null ? "Data de emissão ausente, inválida ou ambígua."
                : issued.isAfter(today) ? "A data de emissão está no futuro."
                : !dateValid ? "A emissão é anterior ao limite de " + maxAgeDays + " dias."
                : "Emissão entre " + today.minusDays(maxAgeDays).format(BR_DATE) + " e " + today.format(BR_DATE) + ", inclusive.";
        rules.add(new RegraValidacao("DATA_EMISSAO", "Emissão nos últimos " + maxAgeDays + " dias", dateValid, dateDetail));

        boolean comparisonRequested = DocumentText.clean(nomeEsperado) != null;
        boolean sameName = comparisonRequested && namePresent
                && DocumentText.normalize(document.nome()).equals(DocumentText.normalize(nomeEsperado));
        rules.add(new RegraValidacao("TITULARIDADE", "Nome corresponde ao titular informado", sameName,
                !comparisonRequested ? "Informe o nome esperado do titular para conferir a titularidade."
                        : sameName ? "Os nomes coincidem após normalizar acentos, caixa e espaços."
                        : "O nome extraído difere do nome informado; abreviações não são consideradas equivalentes."));

        String normalizedText = DocumentText.evidenceText(texto);
        long letters = normalizedText.codePoints().filter(Character::isLetter).count();
        boolean readable = letters >= 30 && normalizedText.split("\\s+").length >= 5
                && fieldsSupportedByText(document, texto);
        rules.add(new RegraValidacao("LEGIBILIDADE", "Texto legível e campos apoiados no documento", readable,
                readable ? "Texto suficiente para leitura automática; os campos extraídos aparecem no texto."
                        : "Texto insuficiente ou pelo menos um campo extraído não foi localizado no texto do documento."));
        return List.copyOf(rules);
    }

    private boolean validPersonName(String value) {
        String clean = DocumentText.clean(value);
        return clean != null && clean.codePoints().filter(Character::isLetter).count() >= 2
                && clean.matches("[\\p{L}\\p{M} .’'-]+");
    }

    private boolean validCity(String value) {
        return validPersonName(value);
    }

    private boolean validAddress(String value) {
        String clean = DocumentText.clean(value);
        return clean != null && clean.length() >= 5 && clean.codePoints().anyMatch(Character::isLetter);
    }

    private boolean fieldsSupportedByText(DadosDocumento document, String text) {
        if (text == null) {
            return false;
        }
        for (String value : new String[]{document.nome(), document.endereco(), document.cidade(), document.estado()}) {
            if (value != null && !DocumentText.containsPhrase(text, value)) {
                return false;
            }
        }
        if (document.cpf() != null && !text.replaceAll("[.\\-\\s]", "").contains(document.cpf())) {
            return false;
        }
        return document.dataEmissao() == null || text.contains(document.dataEmissao().format(BR_DATE))
                || text.contains(document.dataEmissao().format(DateTimeFormatter.ofPattern("dd-MM-uuuu")))
                || text.contains(document.dataEmissao().toString());
    }
}

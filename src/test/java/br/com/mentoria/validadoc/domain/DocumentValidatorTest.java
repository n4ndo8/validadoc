package br.com.mentoria.validadoc.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentValidatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneId.of("America/Fortaleza"));
    private final DocumentValidator validator = new DocumentValidator(CLOCK, 90);

    @Test
    void approvesRecentDocumentAndNormalizesHolderName() {
        DadosDocumento dados = document(TODAY.minusDays(10));
        List<RegraValidacao> rules = validator.validate(dados, text(dados), "  JOAO   da  SILVA ");
        assertTrue(rules.stream().allMatch(RegraValidacao::aprovada), () -> rules.toString());
    }

    @Test
    void acceptsExactlyNinetyDaysAndTodayButRejectsNinetyOneAndTomorrow() {
        assertTrue(ruleForDate(TODAY.minusDays(90)).aprovada());
        assertTrue(ruleForDate(TODAY).aprovada());
        assertFalse(ruleForDate(TODAY.minusDays(91)).aprovada());
        assertFalse(ruleForDate(TODAY.plusDays(1)).aprovada());
    }

    @Test
    void usesTheConfiguredClockTimezoneForTheDateBoundary() {
        Clock lateFortaleza = Clock.fixed(Instant.parse("2026-10-27T01:00:00Z"), ZoneId.of("America/Fortaleza"));
        DocumentValidator localValidator = new DocumentValidator(lateFortaleza, 90);
        DadosDocumento tomorrow = document(TODAY.plusDays(1));
        assertFalse(rule(localValidator.validate(tomorrow, text(tomorrow), null), "DATA_EMISSAO").aprovada());
    }

    @Test
    void missingRequiredFieldsFailEvenWhenTheTextContainsARecognizedTitle() {
        List<RegraValidacao> rules = validator.validate(null, "COMPROVANTE DE RESIDÊNCIA", null);
        for (String code : List.of("NOME", "CPF", "ENDERECO", "DATA_EMISSAO", "LEGIBILIDADE")) {
            assertFalse(rule(rules, code).aprovada(), code);
        }
    }

    @Test
    void cpfPresenceDoesNotPretendToVerifyTheChecksum() {
        DadosDocumento dados = document(TODAY);
        RegraValidacao cpf = rule(validator.validate(dados, text(dados), null), "CPF");
        assertTrue(cpf.aprovada(), "O CPF fictício da referência tem 11 dígitos e deve atender a regra do exercício.");
        assertTrue(cpf.detalhe().contains("não verifica"));
    }

    @Test
    void rejectsDifferentOrAbbreviatedExpectedName() {
        DadosDocumento dados = document(TODAY);
        assertFalse(rule(validator.validate(dados, text(dados), "João Silva"), "TITULARIDADE").aprovada());
        assertFalse(rule(validator.validate(dados, text(dados), "Maria Souza"), "TITULARIDADE").aprovada());
    }

    @Test
    void rejectsAnAbsentExpectedName() {
        DadosDocumento dados = document(TODAY);
        RegraValidacao holder = rule(validator.validate(dados, text(dados), "  "), "TITULARIDADE");
        assertFalse(holder.aprovada());
        assertTrue(holder.detalhe().contains("Informe o nome"));
        assertFalse(rule(validator.validate(dados, text(dados), null), "TITULARIDADE").aprovada());
    }

    @Test
    void rejectsGenericTextEvenWithAllTheExpectedFields() {
        DadosDocumento dados = document(TODAY);
        String generic = text(dados).replace("COMPROVANTE DE RESIDÊNCIA", "CADASTRO DE PARTICIPANTE");
        assertFalse(rule(validator.validate(dados, generic, null), "TIPO_DOCUMENTO").aprovada());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTA DE ÁGUA", "CONTA DE ENERGIA ELÉTRICA", "CONTA DE LUZ",
            "Fatura de telefone", "FATURA DE CARTÃO", "CONTA DE INTERNET"})
    void recognizesTheSupportedAccountTypes(String type) {
        DadosDocumento dados = document(TODAY);
        String source = text(dados).replace("COMPROVANTE DE RESIDÊNCIA", type);
        assertTrue(rule(validator.validate(dados, source, null), "TIPO_DOCUMENTO").aprovada());
    }

    @Test
    void rejectsFieldsNotSupportedByTheSourceText() {
        DadosDocumento dados = document(TODAY);
        String source = text(dados).replace("João da Silva", "Maria Souza");
        assertFalse(rule(validator.validate(dados, source, null), "LEGIBILIDADE").aprovada());
    }

    @Test
    void acceptsHyphenatedDateEvidenceReturnedByTheAiExtractor() {
        DadosDocumento dados = document(TODAY);
        String source = text(dados).replace("26/10/2026", "26-10-2026");
        assertTrue(rule(validator.validate(dados, source, "João da Silva"), "LEGIBILIDADE").aprovada());
    }

    @Test
    void requiresAnActualBrazilianStateAndCoherentFieldFormats() {
        DadosDocumento dados = new DadosDocumento("12345", "123", "x", "123", "XX", TODAY);
        List<RegraValidacao> rules = validator.validate(dados, text(dados), null);
        assertFalse(rule(rules, "NOME").aprovada());
        assertFalse(rule(rules, "CPF").aprovada());
        assertFalse(rule(rules, "ENDERECO").aprovada());
    }

    private RegraValidacao ruleForDate(LocalDate date) {
        DadosDocumento dados = document(date);
        return rule(validator.validate(dados, text(dados), null), "DATA_EMISSAO");
    }

    private static DadosDocumento document(LocalDate date) {
        return new DadosDocumento("João da Silva", "12345678900", "Rua das Flores, 150", "Guanambi", "BA", date);
    }

    private static String text(DadosDocumento dados) {
        return """
                COMPROVANTE DE RESIDÊNCIA
                Nome: %s
                CPF: %s
                Endereço: %s
                Cidade: %s
                UF: %s
                Data de emissão: %s
                """.formatted(dados.nome(), dados.cpf(), dados.endereco(), dados.cidade(), dados.estado(),
                dados.dataEmissao().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")));
    }

    private static RegraValidacao rule(List<RegraValidacao> rules, String code) {
        return rules.stream().filter(rule -> rule.codigo().equals(code)).findFirst().orElseThrow();
    }
}

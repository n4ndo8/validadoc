package br.com.mentoria.validadoc.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalFieldExtractorTest {
    private final LocalFieldExtractor extractor = new LocalFieldExtractor();

    @Test
    void acceptsBrazilianEmissionDateWithHyphens() {
        assertEquals(LocalDate.of(2026, 10, 29),
                extractor.extract("Data de emissão: 29-10-2026").dataEmissao());
        assertEquals(LocalDate.of(2024, 2, 29),
                extractor.extract("Emissão: 29-02-2024").dataEmissao());
    }

    @Test
    void rejectsImpossibleAndMixedSeparatorDates() {
        for (String date : new String[]{"31-02-2026", "29-02-2025", "29-10/2026", "29/10-2026"}) {
            assertNull(extractor.extract("Emissão: " + date).dataEmissao(), date);
        }
    }

    @Test
    void comparesRepeatedDatesAcrossAllSupportedFormats() {
        assertEquals(LocalDate.of(2026, 10, 29), extractor.extract(
                "Emissão: 29-10-2026\nData de emissão: 29/10/2026\nEmitido em: 2026-10-29").dataEmissao());
        assertNull(extractor.extract("Emissão: 29-10-2026\nEmissão: 28/10/2026").dataEmissao());
    }

    @Test
    void doesNotUseHyphenatedDueDateAsEmissionDate() {
        assertNull(extractor.extract("Vencimento: 29-10-2026").dataEmissao());
    }

    @Test
    void extractsTheLabeledPortugueseDocumentFromTheReference() {
        DadosDocumento result = extractor.extract("""
                COMPROVANTE DE RESIDÊNCIA
                Nome: João da Silva
                CPF: 123.456.789-00
                Endereço: Rua das Flores, 150
                Guanambi - BA
                Data de emissão: 10/08/2026
                """);

        assertEquals(new DadosDocumento("João da Silva", "12345678900", "Rua das Flores, 150",
                "Guanambi", "BA", LocalDate.of(2026, 8, 10)), result);
    }

    @Test
    void extractsInlineLabelsAndIsoEmissionDate() {
        DadosDocumento result = extractor.extract("""
                CONTA DE ENERGIA
                Nome: Ana Beatriz CPF: 12345678900
                Endereco: Rua Um, 10 | Cidade: Recife | UF: pe
                Emissao: 2026-10-26
                """);

        assertEquals("Ana Beatriz", result.nome());
        assertEquals("12345678900", result.cpf());
        assertEquals("Rua Um, 10", result.endereco());
        assertEquals("Recife", result.cidade());
        assertEquals("PE", result.estado());
        assertEquals(LocalDate.of(2026, 10, 26), result.dataEmissao());
    }

    @Test
    void acceptsCombinedCityAndStateLabel() {
        DadosDocumento result = extractor.extract("Cidade/UF: São Paulo/SP");
        assertEquals("São Paulo", result.cidade());
        assertEquals("SP", result.estado());
    }

    @Test
    void acceptsCityFollowedByStateWithinTheCityField() {
        DadosDocumento result = extractor.extract("Cidade: Recife - PE");
        assertEquals("Recife", result.cidade());
        assertEquals("PE", result.estado());
    }

    @Test
    void acceptsAnElevenDigitCpfUnderTheSharedCpfCnpjLabel() {
        assertEquals("12345678900", extractor.extract("CPF/CNPJ: 12345678900").cpf());
        assertNull(extractor.extract("CPF/CNPJ: 12345678000190").cpf());
    }

    @Test
    void treatsExplicitMissingValueMarkersAsAbsent() {
        DadosDocumento result = extractor.extract("Nome: não informado\nEndereço: ilegível\nCidade: N/A");
        assertNull(result.nome());
        assertNull(result.endereco());
        assertNull(result.cidade());
    }

    @Test
    void keepsMissingFieldsNullInsteadOfInferringThem() {
        assertEquals(new DadosDocumento(null, null, null, null, null, null), extractor.extract(null));
        assertEquals(new DadosDocumento(null, null, null, null, null, null), extractor.extract(" "));
        DadosDocumento result = extractor.extract("Conta de telefone\nTelefone: 11987654321\nVencimento: 26/10/2026");
        assertNull(result.nome());
        assertNull(result.cpf(), "Um telefone sem rótulo de CPF não deve virar CPF.");
        assertNull(result.dataEmissao(), "Vencimento não equivale a emissão.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"31/02/2026", "29/02/2025", "00/10/2026", "2026-02-30", "2026-13-01", "10/26/2026"})
    void rejectsCalendarInvalidDates(String date) {
        assertNull(extractor.extract("Data de emissão: " + date).dataEmissao());
    }

    @Test
    void recognizesValidLeapDay() {
        assertEquals(LocalDate.of(2024, 2, 29), extractor.extract("Emissão: 29/02/2024").dataEmissao());
    }

    @Test
    void leavesConflictingFieldsNull() {
        DadosDocumento result = extractor.extract("""
                Nome: João da Silva
                Titular: Maria Souza
                CPF: 123.456.789-00
                CPF: 987.654.321-00
                Cidade: Recife
                Cidade: Olinda
                Data de emissão: 25/10/2026
                Emissão: 26/10/2026
                """);

        assertNull(result.nome());
        assertNull(result.cpf());
        assertNull(result.cidade());
        assertNull(result.dataEmissao());
    }

    @Test
    void toleratesRepeatedEquivalentFields() {
        DadosDocumento result = extractor.extract("""
                Nome: João da Silva
                Titular: JOAO  DA SILVA
                CPF: 123.456.789-00
                CPF: 12345678900
                Emissão: 25/10/2026
                Data de emissão: 2026-10-25
                """);

        assertEquals("João da Silva", result.nome());
        assertEquals("12345678900", result.cpf());
        assertEquals(LocalDate.of(2026, 10, 25), result.dataEmissao());
    }

    @Test
    void doesNotTruncateCnpjOrMaskedCpf() {
        assertNull(extractor.extract("CPF: 12345678000190").cpf());
        assertNull(extractor.extract("CNPJ: 12.345.678/0001-90").cpf());
        assertNull(extractor.extract("CPF: ***.456.789-**").cpf());
    }

    @Test
    void rejectsUnknownStateAndContradictoryEmissionDatesInOneLine() {
        assertNull(extractor.extract("Estado: XX").estado());
        assertNull(extractor.extract("Emissão: 25/10/2026 ou 26/10/2026").dataEmissao());
        assertNull(extractor.extract("Emissão: 25/10/2026\nEmissão: ilegível").dataEmissao());
    }
}

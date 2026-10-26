package br.com.mentoria.validadoc.domain;

import java.time.LocalDate;

/** Campos extraídos: um campo ausente ou ambíguo permanece null. */
public record DadosDocumento(
        String nome,
        String cpf,
        String endereco,
        String cidade,
        String estado,
        LocalDate dataEmissao) {
}

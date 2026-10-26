package br.com.mentoria.validadoc.domain;

/** Resultado de uma regra do exercício, sem atestar a autenticidade do documento. */
public record RegraValidacao(String codigo, String descricao, boolean aprovada, String detalhe) {
}

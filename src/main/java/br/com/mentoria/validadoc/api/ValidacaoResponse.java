package br.com.mentoria.validadoc.api;

import java.time.LocalDate;
import java.util.List;

public record ValidacaoResponse(String nomeArquivo, List<Etapa> etapas,
        DadosExtraidos dadosExtraidos, Resultado resultado) {
    public record Etapa(int ordem, String nome, String descricao, String resultado) {}
    public record DadosExtraidos(String nome, String cpf, String endereco, String cidade, LocalDate dataEmissao) {}
    public record Resultado(boolean aprovado, String status, List<String> regrasAtendidas, List<String> pendencias) {}
}

package br.com.mentoria.validadoc.api;

import br.com.mentoria.validadoc.domain.DadosDocumento;
import br.com.mentoria.validadoc.domain.RegraValidacao;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentoResponse(UUID id, String nomeArquivo, Instant criadoEm, String status,
        String metodoExtracao, String textoExtraido, DadosDocumento dados,
        List<RegraValidacao> regras, List<String> avisos) {}

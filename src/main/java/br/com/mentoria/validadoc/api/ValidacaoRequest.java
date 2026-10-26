package br.com.mentoria.validadoc.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ValidacaoRequest(
        @NotBlank @Size(max = 180) String nomeArquivo,
        @NotBlank @Size(max = 100) String mimeType,
        @Size(max = 50_000) String conteudoDocumento,
        @Size(max = 13_981_016) String imagemBase64) {}

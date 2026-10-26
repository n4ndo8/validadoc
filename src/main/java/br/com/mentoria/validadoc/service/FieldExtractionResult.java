package br.com.mentoria.validadoc.service;

import br.com.mentoria.validadoc.domain.DadosDocumento;
import java.util.List;

public record FieldExtractionResult(DadosDocumento dados, String method, List<String> warnings) {}

package br.com.mentoria.validadoc.api;

import br.com.mentoria.validadoc.service.ValidacaoContratoService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documentos")
public class ValidacaoContratoController {
    private final ValidacaoContratoService service;
    public ValidacaoContratoController(ValidacaoContratoService service) { this.service = service; }
    @GetMapping("/exemplo") public ValidacaoRequest exemplo() { return service.exemplo(); }
    @PostMapping(value = "/validar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ValidacaoResponse validar(@Valid @RequestBody ValidacaoRequest request) { return service.validar(request); }
}

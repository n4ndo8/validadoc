package br.com.mentoria.validadoc.api;

import br.com.mentoria.validadoc.service.ExternalServiceException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> invalidJson(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "JSON inválido. Informe nomeArquivo (até 180 caracteres), mimeType e texto (até 50000 caracteres) ou arquivo Base64 (até 10 MB). Não inclua campos desconhecidos.");
    }
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) { return error(HttpStatus.BAD_REQUEST, e.getMessage()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge(Exception e) { return error(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo deve ter no máximo 10 MB."); }
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> missing(Exception e) { return error(HttpStatus.BAD_REQUEST, "Confira o arquivo, o nome esperado e o identificador do envio."); }
    @ExceptionHandler(ExternalServiceException.class)
    ResponseEntity<Map<String, String>> external(ExternalServiceException e) { return error(HttpStatus.BAD_GATEWAY, e.getMessage()); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> unavailable(IllegalStateException e) { return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensagem", ObjectsOrDefault(e.getReason()))); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, String>> unsupported(Exception e) { return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use application/json em /api/documentos/validar ou multipart/form-data em /api/documentos."); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, String>> method(Exception e) { return error(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP não permitido para este endereço."); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> missingResource(Exception e) { return error(HttpStatus.NOT_FOUND, "Recurso não encontrado."); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unknown(Exception e) {
        LOG.error("Falha no processamento ({})", e.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir o processamento. Verifique a configuração e tente novamente.");
    }
    private String ObjectsOrDefault(String value) { return value == null ? "Requisição inválida." : value; }
    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) { return ResponseEntity.status(status).body(Map.of("mensagem", message)); }
}

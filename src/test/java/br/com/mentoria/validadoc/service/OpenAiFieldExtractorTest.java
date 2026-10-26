package br.com.mentoria.validadoc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testa o contrato HTTP sem fazer chamadas externas e sem usar credenciais reais. */
class OpenAiFieldExtractorTest {
    private static final String DOCUMENT = """
            COMPROVANTE DE RESIDÊNCIA
            Nome: Ana Silva
            CPF: 123.456.789-00
            Endereço: Rua das Flores, 150
            Cidade: Recife
            Estado: PE
            Data de emissão: 26/10/2026
            """;
    private static final String FAKE_KEY = "test-not-a-real-key";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    private final AtomicReference<ReceivedRequest> received = new AtomicReference<>();
    private HttpServer server;
    private OpenAiFieldExtractor extractor;

    @BeforeEach
    void startLocalApi() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            try {
                received.set(new ReceivedRequest(exchange.getRequestMethod(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                Reply response = reply.get();
                byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(response.status(), body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        extractor = new OpenAiFieldExtractor(mapper, FAKE_KEY, "test-model", endpoint());
    }

    @AfterEach
    void stopLocalApi() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsStrictSchemaAndReadsCompletedResponseWithEvidence() throws Exception {
        respondWithData(validFields());

        FieldExtractionResult result = extractor.extract(DOCUMENT);

        assertThat(result.dados().nome()).isEqualTo("Ana Silva");
        assertThat(result.dados().cpf()).isEqualTo("12345678900");
        assertThat(result.dados().endereco()).isEqualTo("Rua das Flores, 150");
        assertThat(result.dados().cidade()).isEqualTo("Recife");
        assertThat(result.dados().estado()).isEqualTo("PE");
        assertThat(result.dados().dataEmissao()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(result.method()).isEqualTo("OpenAI / test-model");
        assertThat(result.warnings()).isEmpty();

        ReceivedRequest request = received.get();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.authorization()).isEqualTo("Bearer " + FAKE_KEY);
        assertThat(request.contentType()).isEqualTo("application/json");
        JsonNode body = mapper.readTree(request.body());
        assertThat(body.path("model").asText()).isEqualTo("test-model");
        assertThat(body.path("store").isBoolean()).isTrue();
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.path("input").asText()).isEqualTo(DOCUMENT);
        assertThat(body.path("instructions").asText()).contains("não confiável", "Não invente", "Não decida aprovação");
        JsonNode format = body.path("text").path("format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("strict").asBoolean()).isTrue();
        JsonNode schema = format.path("schema");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(field -> required.add(field.asText()));
        assertThat(required).containsExactly("nome", "cpf", "endereco", "cidade", "estado", "dataEmissao");
        for (String field : required) {
            JsonNode types = schema.path("properties").path(field).path("type");
            assertThat(types.get(0).asText()).isEqualTo("string");
            assertThat(types.get(1).asText()).isEqualTo("null");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void surfacesHttpErrorsWithoutLeakingResponseBodyOrCredentials(int status) {
        reply.set(new Reply(status, "{\"error\":\"sensitive upstream detail\"}"));

        assertThatThrownBy(() -> extractor.extract(DOCUMENT))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("HTTP " + status)
                .hasMessageNotContaining("sensitive upstream detail")
                .hasMessageNotContaining(FAKE_KEY);
    }

    @Test
    void rejectsIncompleteResponseEvenIfItContainsUsableText() throws Exception {
        reply.set(new Reply(200, mapper.writeValueAsString(Map.of(
                "status", "incomplete", "output", List.of(Map.of("content", List.of(
                        Map.of("type", "output_text", "text", mapper.writeValueAsString(validFields())))))))));

        assertThatThrownBy(() -> extractor.extract(DOCUMENT))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("não concluiu");
    }

    @Test
    void rejectsRefusal() throws Exception {
        reply.set(new Reply(200, mapper.writeValueAsString(Map.of("status", "completed", "output", List.of(
                Map.of("content", List.of(Map.of("type", "refusal", "refusal", "Não posso extrair."))))))));

        assertThatThrownBy(() -> extractor.extract(DOCUMENT))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("recusou");
    }

    @Test
    void rejectsMalformedResponseEnvelope() {
        reply.set(new Reply(200, "{invalido"));

        assertThatThrownBy(() -> extractor.extract(DOCUMENT))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("resposta válida");
    }

    @Test
    void rejectsMalformedStructuredOutput() throws Exception {
        respondWithOutput("{nome:");

        assertThatThrownBy(() -> extractor.extract(DOCUMENT))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("resposta válida");
    }

    @Test
    void rejectsMissingOutputText() throws Exception {
        reply.set(new Reply(200, mapper.writeValueAsString(Map.of("status", "completed", "output", List.of()))));

        assertThatThrownBy(() -> extractor.extract(DOCUMENT))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("campos esperados");
    }

    @Test
    void rejectsUnexpectedFieldsAndWrongValueTypes() throws Exception {
        Map<String, Object> withExtraField = validFields();
        withExtraField.put("aprovado", true);
        respondWithData(withExtraField);
        assertThatThrownBy(() -> extractor.extract(DOCUMENT))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("formato esperado");

        Map<String, Object> withWrongType = validFields();
        withWrongType.put("cpf", 12345678900L);
        respondWithData(withWrongType);
        assertThatThrownBy(() -> extractor.extract(DOCUMENT))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("campo inválido: cpf");
    }

    @Test
    void discardsHallucinatedFieldsAndDateWithWarnings() throws Exception {
        Map<String, Object> fields = validFields();
        fields.put("nome", "Pessoa Inventada");
        fields.put("cidade", "Curitiba");
        fields.put("dataEmissao", "2026-10-25");
        respondWithData(fields);

        FieldExtractionResult result = extractor.extract(DOCUMENT);

        assertThat(result.dados().nome()).isNull();
        assertThat(result.dados().cidade()).isNull();
        assertThat(result.dados().dataEmissao()).isNull();
        assertThat(result.dados().cpf()).isEqualTo("12345678900");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("campo nome") && warning.contains("descartado"))
                .anyMatch(warning -> warning.contains("campo cidade") && warning.contains("descartado"))
                .anyMatch(warning -> warning.contains("data") && warning.contains("descartada"));
    }

    @Test
    void convertsImpossibleDateToNullRatherThanSilentlyAdjustingIt() throws Exception {
        Map<String, Object> fields = validFields();
        fields.put("dataEmissao", "2026-02-30");
        respondWithData(fields);

        FieldExtractionResult result = extractor.extract(DOCUMENT + "\nData de emissão: 30/02/2026");

        assertThat(result.dados().dataEmissao()).isNull();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("data inválida"));
    }

    @Test
    void preservesExplicitNullsForMissingFields() throws Exception {
        Map<String, Object> fields = validFields();
        fields.replaceAll((name, value) -> null);
        respondWithData(fields);

        FieldExtractionResult result = extractor.extract("Documento ilegível");

        assertThat(result.dados().nome()).isNull();
        assertThat(result.dados().cpf()).isNull();
        assertThat(result.dados().endereco()).isNull();
        assertThat(result.dados().cidade()).isNull();
        assertThat(result.dados().estado()).isNull();
        assertThat(result.dados().dataEmissao()).isNull();
    }

    @Test
    void failsBeforeNetworkWhenApiKeyIsMissing() {
        OpenAiFieldExtractor withoutKey = new OpenAiFieldExtractor(mapper, " ", "test-model", endpoint());

        assertThatThrownBy(() -> withoutKey.extract(DOCUMENT))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OPENAI_API_KEY");
        assertThat(received.get()).isNull();
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses";
    }

    private Map<String, Object> validFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nome", "Ana Silva");
        fields.put("cpf", "12345678900");
        fields.put("endereco", "Rua das Flores, 150");
        fields.put("cidade", "Recife");
        fields.put("estado", "PE");
        fields.put("dataEmissao", "2026-10-26");
        return fields;
    }

    private void respondWithData(Map<String, Object> fields) throws Exception {
        respondWithOutput(mapper.writeValueAsString(fields));
    }

    private void respondWithOutput(String json) throws Exception {
        reply.set(new Reply(200, mapper.writeValueAsString(Map.of("status", "completed", "output", List.of(
                Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", json))))))));
    }

    private record Reply(int status, String body) { }
    private record ReceivedRequest(String method, String authorization, String contentType, String body) { }
}

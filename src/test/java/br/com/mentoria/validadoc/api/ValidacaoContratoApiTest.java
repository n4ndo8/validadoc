package br.com.mentoria.validadoc.api;

import br.com.mentoria.validadoc.service.SampleDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:contract;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.extraction-mode=LOCAL"})
@AutoConfigureMockMvc
@Import(ValidacaoContratoApiTest.FixedTime.class)
class ValidacaoContratoApiTest {
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneId.of("America/Fortaleza")); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SampleDocumentService sample;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    private String text(LocalDate date) {
        return "COMPROVANTE DE RESIDENCIA\nNome: Joao da Silva\nCPF: 123.456.789-00\n"
            + "Endereco: Rua das Flores, 150\nCidade: Guanambi - BA\nData de emissao: "
            + date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"));
    }
    private ResultActions send(String text, String image, String mime) throws Exception {
        return mvc.perform(post("/api/documentos/validar").contentType("application/json")
            .content(mapper.writeValueAsBytes(new ValidacaoRequest("comprovante.pdf", mime, text, image))));
    }

    @Test void generatedExampleCanBePostedWithoutExpectedOwner() throws Exception {
        String json = mvc.perform(get("/api/documentos/exemplo")).andExpect(status().isOk())
            .andExpect(jsonPath("$.conteudoDocumento", containsString("26/10/2026")))
            .andExpect(jsonPath("$.imagemBase64").value(nullValue())).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/documentos/validar").contentType("application/json").content(json))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resultado.aprovado").value(true))
            .andExpect(jsonPath("$.resultado.status").value("APROVADO"))
            .andExpect(jsonPath("$.etapas", hasSize(5)))
            .andExpect(jsonPath("$.dadosExtraidos.cpf").value("123.456.789-00"))
            .andExpect(jsonPath("$.dadosExtraidos.cidade").value("Guanambi - BA"));
    }
    @Test void literalProfessorExampleHasExpectedShapeAndRules() throws Exception {
        send(text(LocalDate.of(2026,8,10)), null, "application/pdf").andExpect(status().isOk())
            .andExpect(jsonPath("$.dadosExtraidos.dataEmissao").value("2026-08-10"))
            .andExpect(jsonPath("$.dadosExtraidos.nome").value("Joao da Silva"))
            .andExpect(jsonPath("$.resultado.regrasAtendidas", contains("Nome identificado", "CPF encontrado",
                "Endereco encontrado", "Cidade encontrada", "Data de emissao reconhecida", "Emissao dentro de 90 dias")))
            .andExpect(jsonPath("$.resultado.pendencias", empty()))
            .andExpect(jsonPath("$.etapas[1].resultado", containsString("Não executado")))
            .andExpect(jsonPath("$.etapas[2].resultado", containsString("conteudoDocumento")))
            .andExpect(jsonPath("$.etapas[3].resultado", containsString("sem IA")));
    }
    @ParameterizedTest @ValueSource(ints = {0,90})
    void acceptsDateBoundaries(int days) throws Exception {
        send(text(TODAY.minusDays(days)),null,"application/pdf").andExpect(jsonPath("$.resultado.aprovado").value(true));
    }
    @ParameterizedTest @ValueSource(ints = {-1,91})
    void rejectsFutureAndExpiredDates(int days) throws Exception {
        send(text(TODAY.minusDays(days)),null,"application/pdf").andExpect(status().isOk())
            .andExpect(jsonPath("$.resultado.aprovado").value(false)).andExpect(jsonPath("$.resultado.pendencias", not(empty())));
    }
    @Test void missingFieldsAreBusinessRejection() throws Exception {
        send("texto sem campos",null,"application/pdf").andExpect(status().isOk())
            .andExpect(jsonPath("$.resultado.status").value("REPROVADO"))
            .andExpect(jsonPath("$.resultado.pendencias", hasSize(5)));
    }
    @Test void rejectsMissingInputsBadJsonAndUnknownFields() throws Exception {
        send(" ",null,"application/pdf").andExpect(status().isBadRequest());
        for (String json : new String[]{"{}", "{", "null", "{\"nomeArquivo\":\"a.pdf\",\"mimeType\":\"application/pdf\",\"inesperado\":true}"})
            mvc.perform(post("/api/documentos/validar").contentType("application/json").content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").exists());
    }
    @Test void rejectsOversizedTextAndBase64() throws Exception {
        send("x".repeat(50_001),null,"application/pdf").andExpect(status().isBadRequest());
        send(null,"A".repeat(13_981_017),"image/png").andExpect(status().isBadRequest());
    }
    @Test void invalidImageCannotBeHiddenByValidFallbackText() throws Exception {
        send(text(TODAY),"not base64!","image/png").andExpect(status().isBadRequest());
        send(null,Base64.getEncoder().encodeToString("not PNG".getBytes()),"image/png").andExpect(status().isBadRequest());
        send(text(TODAY),null,"text/html").andExpect(status().isBadRequest());
    }
    @Test void pdfBase64UsesRealTextLayerAndTakesPriority() throws Exception {
        send(text(TODAY),Base64.getEncoder().encodeToString(sample.create()),"application/pdf")
            .andExpect(status().isOk()).andExpect(jsonPath("$.dadosExtraidos.nome").value("Ana Silva"))
            .andExpect(jsonPath("$.resultado.aprovado").value(true))
            .andExpect(jsonPath("$.etapas[1].resultado", containsString("Não executado")))
            .andExpect(jsonPath("$.etapas[2].resultado", containsString("PDFBox")))
            .andExpect(jsonPath("$.etapas[2].resultado", containsString("ignorado")));
        send(null,Base64.getEncoder().encodeToString(sample.create()),"image/png").andExpect(status().isBadRequest());
    }
    @Test void pngBase64ExecutesRealOcr() throws Exception {
        BufferedImage image = new BufferedImage(1800,1100,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0,0,1800,1100); g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,46));
            int y=130;
            for(String line:text(TODAY).split("\n")) { g.drawString(line,100,y); y+=110; }
        } finally { g.dispose(); }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image,"png",bytes));
            send(null,Base64.getEncoder().encodeToString(bytes.toByteArray()),"image/png")
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado.aprovado").value(true))
                .andExpect(jsonPath("$.dadosExtraidos.nome").value("Joao da Silva"))
                .andExpect(jsonPath("$.etapas[2].resultado", containsString("Tesseract")))
                .andExpect(jsonPath("$.resultado.regrasAtendidas", hasItem("Confianca do OCR de pelo menos 60%")));
        } finally { image.flush(); }
    }
}

package br.com.mentoria.validadoc.api;

import br.com.mentoria.validadoc.persistence.DocumentoRepository;
import br.com.mentoria.validadoc.service.SampleDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:apitest;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "app.extraction-mode=LOCAL"})
@AutoConfigureMockMvc
class DocumentoApiTest {
    @Autowired MockMvc mvc;
    @Autowired SampleDocumentService sample;
    @Autowired DocumentoRepository repository;

    @BeforeEach void clear() { repository.deleteAll(); }

    @Test void uploadsExtractsValidatesAndPersistsRealPdf() throws Exception {
        String location = mvc.perform(multipart("/api/documentos")
                .file(new MockMultipartFile("arquivo", "exemplo.pdf", "application/pdf", sample.create()))
                .param("nomeEsperado", "Ana Silva"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APROVADO"))
            .andExpect(jsonPath("$.dados.nome").value("Ana Silva"))
            .andExpect(jsonPath("$.dados.cpf").value("12345678900"))
            .andExpect(jsonPath("$.dados.cidade").value("Recife"))
            .andExpect(jsonPath("$.metodoExtracao", containsString("PDFBox")))
            .andExpect(jsonPath("$.regras[*].aprovada", everyItem(is(true))))
            .andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.nomeArquivo").value("exemplo.pdf"));
        mvc.perform(get("/api/documentos")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test void wrongOwnerRejectsDocumentWithReason() throws Exception {
        mvc.perform(multipart("/api/documentos")
                .file(new MockMultipartFile("arquivo", "exemplo.pdf", "application/pdf", sample.create()))
                .param("nomeEsperado", "Outra Pessoa"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REPROVADO"))
            .andExpect(jsonPath("$.regras[?(@.codigo == 'TITULARIDADE')].aprovada", contains(false)));
    }

    @Test void disguisedTextIsRejectedAndNotPersisted() throws Exception {
        mvc.perform(multipart("/api/documentos")
                .file(new MockMultipartFile("arquivo", "conta.pdf", "application/pdf", "texto sem assinatura PDF".getBytes()))
                .param("nomeEsperado", "Ana Silva"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem", containsString("Formato")));
        mvc.perform(get("/api/documentos")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test void requiresFileAndName() throws Exception {
        mvc.perform(multipart("/api/documentos").param("nomeEsperado", "Ana Silva"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").exists());
        mvc.perform(multipart("/api/documentos")
                .file(new MockMultipartFile("arquivo", "exemplo.pdf", "application/pdf", sample.create()))
                .param("nomeEsperado", " "))
            .andExpect(status().isBadRequest());
    }

    @Test void missingIdAndOversizedUploadHaveUsefulErrors() throws Exception {
        mvc.perform(get("/api/documentos/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensagem").exists());
        mvc.perform(multipart("/api/documentos")
                .file(new MockMultipartFile("arquivo", "grande.png", "image/png", new byte[10 * 1024 * 1024 + 1]))
                .param("nomeEsperado", "Ana Silva"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem", containsString("10 MB")));
    }

    @Test void servesConfigurationAndSample() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isOk()).andExpect(jsonPath("$.extractionMode").value("LOCAL"));
        mvc.perform(get("/api/exemplo.pdf")).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", containsString("comprovante-exemplo.pdf")));
    }

    @Test void preservesHttpErrorStatuses() throws Exception {
        mvc.perform(get("/api/inexistente")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/config")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/documentos").contentType("application/json").content("{}"))
            .andExpect(status().isUnsupportedMediaType());
    }
}

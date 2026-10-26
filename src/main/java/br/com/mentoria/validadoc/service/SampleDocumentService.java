package br.com.mentoria.validadoc.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;

@Service
public class SampleDocumentService {
    private final Clock clock;
    public SampleDocumentService(Clock clock) { this.clock = clock; }

    public byte[] create() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(1f, 83 / 255f, 0f);
                content.addRect(0, 760, 595, 82); content.fill();
                content.beginText(); content.setNonStrokingColor(1f, 1f, 1f);
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 22);
                content.newLineAtOffset(44, 792); content.showText("COMPROVANTE DE RESIDÊNCIA"); content.endText();
                content.beginText(); content.setNonStrokingColor(30 / 255f, 40 / 255f, 55 / 255f);
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 15);
                content.setLeading(30); content.newLineAtOffset(44, 700);
                String[] lines = {"EXEMPLO FICTÍCIO - SEM VALOR DOCUMENTAL", "",
                    "Nome: Ana Silva", "CPF: 123.456.789-00", "Endereço: Rua das Flores, 150",
                    "Cidade: Recife", "Estado: PE", "Data de emissão: " + LocalDate.now(clock).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),
                    "", "Amostra criada para demonstrar o projeto ValidaDoc.", "Os dados acima são fictícios."};
                for (String line : lines) { content.showText(line); content.newLine(); }
                content.endText();
            }
            document.save(bytes); return bytes.toByteArray();
        }
    }
}

package br.com.mentoria.validadoc.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documentos", indexes = @Index(name = "idx_documentos_criado_em", columnList = "criadoEm"))
public class DocumentoEntity {
    @Id private UUID id;
    @Column(nullable = false) private Instant criadoEm;
    @Column(nullable = false, columnDefinition = "TEXT") private String resultadoJson;

    protected DocumentoEntity() {}
    public DocumentoEntity(UUID id, Instant criadoEm, String resultadoJson) {
        this.id = id; this.criadoEm = criadoEm; this.resultadoJson = resultadoJson;
    }
    public String getResultadoJson() { return resultadoJson; }
}

package br.com.vanroute.backend.models.document;

import br.com.vanroute.backend.models.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

// alguem assinou o documento (o motorista ao propor o contrato, o responsavel ao aceitar).
// guarda a hora e o hash dos termos assinados: se o contrato mudar, o hash nao bate mais
@Entity
@Table(name = "signatures")
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "document_hash", nullable = false, length = 64)
    private String documentHash;

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(LocalDateTime signedAt) {
        this.signedAt = signedAt;
    }

    public String getDocumentHash() {
        return documentHash;
    }

    public void setDocumentHash(String documentHash) {
        this.documentHash = documentHash;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}

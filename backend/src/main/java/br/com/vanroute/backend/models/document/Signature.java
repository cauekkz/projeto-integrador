package br.com.vanroute.backend.models.document;

import br.com.vanroute.backend.models.document.enums.SignatureStatus;
import br.com.vanroute.backend.models.user.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "signatures")
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column()
    private SignatureStatus status;

    @Column(name = "signed_at")
    private LocalDate signedAt;

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

    public SignatureStatus getStatus() {
        return status;
    }

    public void setStatus(SignatureStatus status) {
        this.status = status;
    }

    public LocalDate getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(LocalDate signedAt) {
        this.signedAt = signedAt;
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

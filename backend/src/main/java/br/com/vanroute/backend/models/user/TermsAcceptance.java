package br.com.vanroute.backend.models.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

// o usuario aceitou essa versao dos termos nesse momento
@Entity
@Table(name = "terms_acceptances")
public class TermsAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    protected TermsAcceptance() {
    }

    public TermsAcceptance(UUID userId, String version, LocalDateTime acceptedAt) {
        this.userId = userId;
        this.version = version;
        this.acceptedAt = acceptedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getVersion() {
        return version;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }
}

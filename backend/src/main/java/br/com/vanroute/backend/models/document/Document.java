package br.com.vanroute.backend.models.document;

import br.com.vanroute.backend.models.document.enums.DocumentEntityType;
import br.com.vanroute.backend.models.document.enums.DocumentStatus;
import br.com.vanroute.backend.models.document.enums.DocumentType;
import br.com.vanroute.backend.models.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(columnDefinition = "TEXT")
    private String url;

    private Integer version;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column()
    private DocumentType type;

    @Enumerated(EnumType.STRING)
    @Column()
    private DocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type")
    private DocumentEntityType entityType;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @ManyToOne
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public DocumentEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(DocumentEntityType entityType) {
        this.entityType = entityType;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

}

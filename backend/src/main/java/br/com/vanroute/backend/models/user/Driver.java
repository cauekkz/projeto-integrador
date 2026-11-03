package br.com.vanroute.backend.models.user;

import br.com.vanroute.backend.models.user.enums.DriverApprovalStatus;
import br.com.vanroute.backend.models.user.enums.PixKeyType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @Column(name = "user_id")
    private UUID userId;
/* 
    @Column(name = "cnh_number", nullable = false, unique = true)
    private String cnhNumber;

    @Column(name = "cnh_expiration")
    private LocalDate cnhExpiration;
*/
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    private DriverApprovalStatus approvalStatus;
/* 
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private DriverType type;
*/
    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    public UUID getUserId() {
        return userId;
    }
    @Column(name = "link_code", length = 9, nullable = false, unique = true)
    private String linkCode;

    public String getLinkCode() {
        return linkCode;
    }

    public void setLinkCode(String linkCode) {
        this.linkCode = linkCode;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
/* 
    public String getCnhNumber() {
        return cnhNumber;
    }

    public void setCnhNumber(String cnhNumber) {
        this.cnhNumber = cnhNumber;
    }

    public LocalDate getCnhExpiration() {
        return cnhExpiration;
    }

    public void setCnhExpiration(LocalDate cnhExpiration) {
        this.cnhExpiration = cnhExpiration;
    }
*/
    public DriverApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(DriverApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }
/* 
    public DriverType getType() {
        return type;
    }

    public void setType(DriverType type) {
        this.type = type;
    }Password
*/
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    // chave Pix que o responsavel copia pra pagar as parcelas (o motorista confirma o recebimento)
    @Enumerated(EnumType.STRING)
    @Column(name = "pix_key_type", length = 10)
    private PixKeyType pixKeyType;

    @Column(name = "pix_key", length = 77)
    private String pixKey;

    public PixKeyType getPixKeyType() {
        return pixKeyType;
    }

    public String getPixKey() {
        return pixKey;
    }

    public void setPix(PixKeyType pixKeyType, String pixKey) {
        this.pixKeyType = pixKeyType;
        this.pixKey = pixKey;
    }

}

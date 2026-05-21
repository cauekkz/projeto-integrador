package br.com.vanroute.backend.models.user;

import br.com.vanroute.backend.models.user.enums.FinancialStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "responsibles")
public class Responsible {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_status", nullable = false)
    private FinancialStatus financialStatus;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public FinancialStatus getFinancialStatus() {
        return financialStatus;
    }

    public void setFinancialStatus(FinancialStatus financialStatus) {
        this.financialStatus = financialStatus;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

}

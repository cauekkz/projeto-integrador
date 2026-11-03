package br.com.vanroute.backend.models.contract;

import br.com.vanroute.backend.models.contract.enums.CancellationStatus;
import br.com.vanroute.backend.models.user.User;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// pedido de cancelamento com aviso previo: vale sozinho na effectiveDate, a nao ser que a outra parte aceite antes
// (cancela na hora) ou quem pediu desista. Fica como historico mesmo depois de decidido
@Entity
@Table(name = "contract_cancellations")
public class ContractCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CancellationStatus status;

    // o motorista cancelou na hora, sem aviso (parcela vencida ha 30 dias ou mais)
    @Column(nullable = false)
    private boolean unilateral;

    // fim do aviso previo: o contrato e cancelado nesse dia se ninguem fizer nada (so no pedido PENDING)
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @ManyToOne
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public UUID getId() {
        return id;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(User requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public CancellationStatus getStatus() {
        return status;
    }

    public boolean isUnilateral() {
        return unilateral;
    }

    public void setUnilateral(boolean unilateral) {
        this.unilateral = unilateral;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setStatus(CancellationStatus status) {
        this.status = status;
    }

    // fecha o pedido: quem decidiu e quando
    public void decide(CancellationStatus status, User decidedBy, LocalDateTime decidedAt) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }
}

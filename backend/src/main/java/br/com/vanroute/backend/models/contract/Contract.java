package br.com.vanroute.backend.models.contract;

import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "party_a_type")
    private String partyAType;

    @Column(name = "party_a_id")
    private Long partyAId;

    @Column(name = "party_b_type")
    private String partyBType;

    @Column(name = "party_b_id")
    private Long partyBId;

    @Column(length = 100)
    private String periodicity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column()
    private ContractStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPartyAType() {
        return partyAType;
    }

    public void setPartyAType(String partyAType) {
        this.partyAType = partyAType;
    }

    public Long getPartyAId() {
        return partyAId;
    }

    public void setPartyAId(Long partyAId) {
        this.partyAId = partyAId;
    }

    public String getPartyBType() {
        return partyBType;
    }

    public void setPartyBType(String partyBType) {
        this.partyBType = partyBType;
    }

    public Long getPartyBId() {
        return partyBId;
    }

    public void setPartyBId(Long partyBId) {
        this.partyBId = partyBId;
    }

    public String getPeriodicity() {
        return periodicity;
    }

    public void setPeriodicity(String periodicity) {
        this.periodicity = periodicity;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

}

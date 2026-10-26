package br.com.vanroute.backend.dtos.contract;

import br.com.vanroute.backend.models.contract.enums.ContractPeriodicity;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// item da lista "meus contratos"; o detalhe completo (endereco, documento) vem em GET /api/contract/{id}
public record ContractSummaryResponseDTO(
        UUID contractId,
        UUID userDriverContractId,
        ContractStatus status,
        ContractPeriodicity periodicity,
        BigDecimal value,
        LocalDate startDate,
        LocalDate endDate,
        UUID studentId,
        String studentName,
        UUID schoolId,
        String schoolName,
        UUID driverId,
        String driverName,
        UUID responsibleId,
        String responsibleName
) {
}

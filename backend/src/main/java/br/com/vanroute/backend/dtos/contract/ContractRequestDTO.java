package br.com.vanroute.backend.dtos.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import br.com.vanroute.backend.models.contract.enums.ContractPeriodicity;

public record ContractRequestDTO(
    ContractPeriodicity periodicity,
    BigDecimal value,
    LocalDate startDate,
    LocalDate endDate
) {
}
package br.com.vanroute.backend.dtos.contract;

import br.com.vanroute.backend.models.contract.enums.ContractStatus;

import java.util.UUID;

// filtros opcionais de GET /api/contract/my-contracts (?status=&studentId=&driverId=)
public record MyContractsFilterRequestDTO(
        ContractStatus status,
        UUID studentId,
        UUID driverId
) {
}

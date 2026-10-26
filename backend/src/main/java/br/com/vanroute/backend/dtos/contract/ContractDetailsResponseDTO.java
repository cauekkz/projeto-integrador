package br.com.vanroute.backend.dtos.contract;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;
import br.com.vanroute.backend.models.contract.enums.ContractPeriodicity;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.document.enums.DocumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// o que o responsavel precisa ver pra decidir sobre a proposta
public record ContractDetailsResponseDTO(
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
        AddressResponseDTO schoolAddress,
        UUID driverId,
        String driverName,
        UUID responsibleId,
        String responsibleName,
        // fileKey no MinIO; baixar via GET /api/storage/download-url
        String documentFileKey,
        DocumentType documentType
) {
}

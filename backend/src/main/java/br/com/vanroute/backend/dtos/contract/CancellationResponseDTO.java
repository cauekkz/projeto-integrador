package br.com.vanroute.backend.dtos.contract;

import br.com.vanroute.backend.models.contract.ContractCancellation;
import br.com.vanroute.backend.models.contract.enums.CancellationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// pedido de cancelamento; effectiveDate = fim do aviso previo (o contrato cai nesse dia se ninguem fizer nada);
// unilateral = o motorista cancelou na hora por parcela vencida ha 30 dias ou mais
public record CancellationResponseDTO(
        UUID id,
        CancellationStatus status,
        boolean unilateral,
        String reason,
        UUID requestedById,
        String requestedByName,
        LocalDateTime requestedAt,
        LocalDate effectiveDate,
        UUID decidedById,
        String decidedByName,
        LocalDateTime decidedAt
) {
    public static CancellationResponseDTO of(ContractCancellation c) {
        return new CancellationResponseDTO(c.getId(), c.getStatus(), c.isUnilateral(), c.getReason(),
                c.getRequestedBy().getId(), c.getRequestedBy().getName(), c.getRequestedAt(), c.getEffectiveDate(),
                c.getDecidedBy() == null ? null : c.getDecidedBy().getId(),
                c.getDecidedBy() == null ? null : c.getDecidedBy().getName(),
                c.getDecidedAt());
    }
}

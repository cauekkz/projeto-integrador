package br.com.vanroute.backend.dtos.contract;

import br.com.vanroute.backend.models.contract.ContractCancellation;
import br.com.vanroute.backend.models.contract.enums.CancellationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

// pedido de cancelamento; unilateral = o motorista cancelou sozinho por parcelas atrasadas
public record CancellationResponseDTO(
        UUID id,
        CancellationStatus status,
        boolean unilateral,
        String reason,
        UUID requestedById,
        String requestedByName,
        LocalDateTime requestedAt,
        UUID decidedById,
        String decidedByName,
        LocalDateTime decidedAt
) {
    public static CancellationResponseDTO of(ContractCancellation c) {
        return new CancellationResponseDTO(c.getId(), c.getStatus(), c.isUnilateral(), c.getReason(),
                c.getRequestedBy().getId(), c.getRequestedBy().getName(), c.getRequestedAt(),
                c.getDecidedBy() == null ? null : c.getDecidedBy().getId(),
                c.getDecidedBy() == null ? null : c.getDecidedBy().getName(),
                c.getDecidedAt());
    }
}

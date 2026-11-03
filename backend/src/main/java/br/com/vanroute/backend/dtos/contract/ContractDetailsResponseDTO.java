package br.com.vanroute.backend.dtos.contract;

import br.com.vanroute.backend.models.student.enums.RelationType;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;
import br.com.vanroute.backend.models.contract.enums.ContractPeriodicity;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.document.enums.DocumentStatus;
import br.com.vanroute.backend.models.document.enums.DocumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        DocumentType documentType,
        // PENDING ate o responsavel aceitar; SIGNED (as duas partes assinaram) ou REJECTED
        DocumentStatus documentStatus,
        // quem assinou e quando; valid = os termos de hoje ainda sao os que foram assinados
        List<SignatureItem> signatures,
        // pedidos de cancelamento (pendente e historico), do mais antigo pro mais novo
        List<CancellationResponseDTO> cancellations,
        // parcelas vencidas e nao pagas; com 2+ o motorista pode cancelar sozinho
        long overduePayments,
        // todos os responsaveis do aluno (o admin primeiro): o motorista ve quem e quem, sem telefone
        List<StudentResponsibleItem> studentResponsibles
) {
    public record StudentResponsibleItem(UUID responsibleId, String name, RelationType relationType, boolean admin) {
    }

    public record SignatureItem(UUID userId, String name, LocalDateTime signedAt, boolean valid) {
    }
}

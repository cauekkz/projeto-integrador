package br.com.vanroute.backend.dtos.user;

import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.student.enums.RelationType;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;

import java.util.List;
import java.util.UUID;

// perfil do responsavel. Pro motorista, financialStatus e overduePayments sao so das parcelas com ele (atraso com
// outros motoristas nao aparece); no proprio perfil, de todos os contratos
public record ResponsibleProfileResponseDTO(
        UUID id,
        String name,
        // fileKey da foto (GET /api/storage/download-url), ou null
        String photoKey,
        String phone,
        FinancialStatus financialStatus,
        long overduePayments,
        List<StudentItem> students
) {
    // filho com contrato com esse motorista; relationType e o rotulo do vinculo (FINANCIAL, LEGAL, WITHDRAWAL)
    public record StudentItem(UUID studentId, String studentName, UUID contractId, ContractStatus contractStatus,
                              RelationType relationType, boolean admin) {
    }
}

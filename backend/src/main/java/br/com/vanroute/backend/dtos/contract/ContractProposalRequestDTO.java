package br.com.vanroute.backend.dtos.contract;

import java.util.UUID;
import br.com.vanroute.backend.dtos.document.DocumentRequestDTO;
public record ContractProposalRequestDTO(
    UUID responsibleId,
    UUID studentId,
    UUID schoolId,
    ContractRequestDTO contract,
    DocumentRequestDTO document
) {}
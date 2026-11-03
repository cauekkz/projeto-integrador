package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.contract.ContractCancellation;
import br.com.vanroute.backend.models.contract.enums.CancellationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractCancellationRepository extends JpaRepository<ContractCancellation, UUID> {

    Optional<ContractCancellation> findByContractIdAndStatus(UUID contractId, CancellationStatus status);

    boolean existsByContractIdAndStatus(UUID contractId, CancellationStatus status);

    // historico do contrato, do mais antigo pro mais novo
    @Query("SELECT c FROM ContractCancellation c JOIN FETCH c.requestedBy LEFT JOIN FETCH c.decidedBy " +
            "WHERE c.contract.id = :contractId ORDER BY c.requestedAt")
    List<ContractCancellation> findHistory(@Param("contractId") UUID contractId);

    // pedidos pendentes de uma lista de contratos (lista "meus contratos" numa consulta so)
    @Query("SELECT c FROM ContractCancellation c JOIN FETCH c.requestedBy " +
            "WHERE c.contract.id IN :contractIds AND c.status = br.com.vanroute.backend.models.contract.enums.CancellationStatus.PENDING")
    List<ContractCancellation> findPendingOf(@Param("contractIds") Collection<UUID> contractIds);
}

package br.com.vanroute.backend.repositories;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.vanroute.backend.models.contract.Contract;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    // SELECT ... FOR UPDATE: toda mudanca de status (aceitar, recusar, retirar, cancelar) le o contrato assim
    // primeiro. Duas requisicoes iguais ao mesmo tempo (duplo clique) ou o worker na mesma linha esperam uma a
    // outra, e a segunda ja ve o status novo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Contract c WHERE c.id = :id")
    Optional<Contract> findByIdForUpdate(@Param("id") UUID id);
}

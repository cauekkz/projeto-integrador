package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.contract.UserDriverContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDriverContractRepository extends JpaRepository<UserDriverContract, UUID> {

    boolean existsByDriverIdAndStudentIdAndContractStatusIn(UUID driverId, UUID studentId, List<ContractStatus> statuses);

    Optional<UserDriverContract> findByContractId(UUID contractId);
}

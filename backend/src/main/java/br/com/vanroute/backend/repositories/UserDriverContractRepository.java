package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.contract.UserDriverContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDriverContractRepository extends JpaRepository<UserDriverContract, UUID> {

    boolean existsByDriverUserIdAndStudentIdAndContractStatusIn(UUID driverId, UUID studentId, List<ContractStatus> statuses);

    Optional<UserDriverContract> findByContractId(UUID contractId);

    // detalhe do contrato numa consulta so, em vez de uma por relacao lazy
    @Query("SELECT u FROM UserDriverContract u " +
            "JOIN FETCH u.contract JOIN FETCH u.student " +
            "JOIN FETCH u.school s LEFT JOIN FETCH s.address " +
            "JOIN FETCH u.driver d JOIN FETCH d.user " +
            "JOIN FETCH u.responsible r JOIN FETCH r.user " +
            "WHERE u.contract.id = :contractId")
    Optional<UserDriverContract> findDetailsByContractId(@Param("contractId") UUID contractId);
    Optional<UserDriverContract> findFirstByDriverUserIdAndStudentIdAndContractStatus(UUID driverId, UUID studentId, ContractStatus status);
}

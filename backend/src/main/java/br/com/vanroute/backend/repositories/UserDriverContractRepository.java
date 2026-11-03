package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.contract.UserDriverContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDriverContractRepository extends JpaRepository<UserDriverContract, UUID> {

    // o responsavel assinou contrato com o aluno num desses status (sair do aluno ou ser removido)
    boolean existsByResponsibleUserIdAndStudentIdAndContractStatusIn(UUID responsibleId, UUID studentId, List<ContractStatus> statuses);

    Optional<UserDriverContract> findByContractId(UUID contractId);

    // nova proposta pro mesmo motorista e aluno: no maximo o contrato atual e UM proximo. Bloqueia se ja existe
    // proposta esperando, ou um contrato agendado (SCHEDULED: a renovacao ja foi feita), ou um ACTIVE no mesmo
    // periodo. Nunca existem dois contratos valendo ao mesmo tempo, nem uma fila de contratos futuros
    @Query("SELECT COUNT(u) > 0 FROM UserDriverContract u JOIN u.contract c " +
            "WHERE u.driver.userId = :driverId AND u.student.id = :studentId AND (" +
            "c.status IN (br.com.vanroute.backend.models.contract.enums.ContractStatus.PENDING, " +
            "br.com.vanroute.backend.models.contract.enums.ContractStatus.SCHEDULED) OR (" +
            "c.status = br.com.vanroute.backend.models.contract.enums.ContractStatus.ACTIVE " +
            "AND c.startDate <= :endDate AND c.endDate >= :startDate))")
    boolean existsPendingOrOverlapping(@Param("driverId") UUID driverId, @Param("studentId") UUID studentId,
                                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // outro contrato aceito (SCHEDULED ou ACTIVE) do mesmo motorista com o aluno, alem desse: ao encerrar ou cancelar
    // um, o aluno continua nas rotas e grupos (renovacao)
    @Query("SELECT COUNT(u) > 0 FROM UserDriverContract u JOIN u.contract c " +
            "WHERE u.driver.userId = :driverId AND u.student.id = :studentId AND c.id <> :contractId " +
            "AND c.status IN (br.com.vanroute.backend.models.contract.enums.ContractStatus.SCHEDULED, " +
            "br.com.vanroute.backend.models.contract.enums.ContractStatus.ACTIVE)")
    boolean existsOtherAccepted(@Param("driverId") UUID driverId, @Param("studentId") UUID studentId,
                                @Param("contractId") UUID contractId);

    // alunos com contrato ACTIVE com o motorista (so esses entram numa viagem)
    @Query("SELECT u.student.id FROM UserDriverContract u WHERE u.driver.userId = :driverId " +
            "AND u.contract.status = br.com.vanroute.backend.models.contract.enums.ContractStatus.ACTIVE")
    List<UUID> findStudentIdsWithActiveContract(@Param("driverId") UUID driverId);

    // detalhe do contrato numa consulta so, em vez de uma por relacao lazy
    @Query("SELECT u FROM UserDriverContract u " +
            "JOIN FETCH u.contract JOIN FETCH u.student " +
            "JOIN FETCH u.school s LEFT JOIN FETCH s.address " +
            "JOIN FETCH u.driver d JOIN FETCH d.user " +
            "JOIN FETCH u.responsible r JOIN FETCH r.user " +
            "WHERE u.contract.id = :contractId")
    Optional<UserDriverContract> findDetailsByContractId(@Param("contractId") UUID contractId);

    // contratos em que o usuario e o motorista ou o responsavel
    @Query("SELECT u FROM UserDriverContract u " +
            "JOIN FETCH u.contract c JOIN FETCH u.student JOIN FETCH u.school " +
            "JOIN FETCH u.driver d JOIN FETCH d.user JOIN FETCH u.responsible r JOIN FETCH r.user " +
            "WHERE (d.userId = :userId OR r.userId = :userId) " +
            "ORDER BY c.startDate DESC")
    List<UserDriverContract> findAllOfUser(@Param("userId") UUID userId);

    @Query("SELECT u FROM UserDriverContract u " +
            "JOIN FETCH u.contract c JOIN FETCH u.student JOIN FETCH u.school " +
            "JOIN FETCH u.driver d JOIN FETCH d.user JOIN FETCH u.responsible r JOIN FETCH r.user " +
            "WHERE (d.userId = :userId OR r.userId = :userId) AND c.status = :status " +
            "ORDER BY c.startDate DESC")
    List<UserDriverContract> findAllOfUserByStatus(@Param("userId") UUID userId, @Param("status") ContractStatus status);
    Optional<UserDriverContract> findFirstByDriverUserIdAndStudentIdAndContractStatus(UUID driverId, UUID studentId, ContractStatus status);

    // o responsavel tem (ou teve) contrato com o motorista, num desses status
    boolean existsByDriverUserIdAndResponsibleUserIdAndContractStatusIn(UUID driverId, UUID responsibleId, List<ContractStatus> statuses);

    boolean existsByDriverUserIdAndResponsibleUserId(UUID driverId, UUID responsibleId);

    // o usuario (motorista ou responsavel) tem contrato num desses status
    @Query("SELECT COUNT(u) > 0 FROM UserDriverContract u WHERE (u.driver.userId = :userId OR u.responsible.userId = :userId) " +
            "AND u.contract.status IN :statuses")
    boolean existsOfUserWithStatus(@Param("userId") UUID userId, @Param("statuses") List<ContractStatus> statuses);
}

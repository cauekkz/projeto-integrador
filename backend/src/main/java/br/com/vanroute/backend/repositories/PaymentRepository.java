package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.contract.Payment;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // atrasada = OVERDUE (o worker diario marca) ou PENDING vencida que o worker ainda nao marcou
    String OVERDUE = "(p.status = br.com.vanroute.backend.models.contract.enums.PaymentStatus.OVERDUE OR (p.status = br.com.vanroute.backend.models.contract.enums.PaymentStatus.PENDING AND p.dueDate < :today))";

    // dar baixa ou cancelar le a parcela travada: duas confirmacoes ao mesmo tempo nao passam as duas
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    // tem parcela nao paga vencida ate essa data (hoje - 30 dias: o motorista pode cancelar na hora)
    @Query("SELECT COUNT(p) > 0 FROM Payment p WHERE p.contract.id = :contractId AND p.dueDate <= :limit AND " +
            "p.status IN (br.com.vanroute.backend.models.contract.enums.PaymentStatus.PENDING, " +
            "br.com.vanroute.backend.models.contract.enums.PaymentStatus.OVERDUE)")
    boolean existsUnpaidDueUntil(@Param("contractId") UUID contractId, @Param("limit") LocalDate limit);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.contract.id = :contractId AND " + OVERDUE)
    long countOverdue(@Param("contractId") UUID contractId, @Param("today") LocalDate today);

    // parcelas atrasadas so dos contratos entre esse motorista e esse responsavel (perfil do responsavel)
    @Query("SELECT COUNT(p) FROM Payment p, UserDriverContract u WHERE u.contract = p.contract " +
            "AND u.driver.userId = :driverId AND u.responsible.userId = :responsibleId AND " + OVERDUE)
    long countOverdueBetween(@Param("driverId") UUID driverId, @Param("responsibleId") UUID responsibleId, @Param("today") LocalDate today);

    // parcelas atrasadas em qualquer contrato do usuario, como motorista ou responsavel (excluir conta)
    @Query("SELECT COUNT(p) FROM Payment p, UserDriverContract u WHERE u.contract = p.contract " +
            "AND (u.driver.userId = :userId OR u.responsible.userId = :userId) AND " + OVERDUE)
    long countOverdueOfUser(@Param("userId") UUID userId, @Param("today") LocalDate today);

    // parcelas atrasadas de todos os contratos do responsavel (situacao financeira dele)
    @Query("SELECT COUNT(p) FROM Payment p, UserDriverContract u WHERE u.contract = p.contract " +
            "AND u.responsible.userId = :responsibleId AND " + OVERDUE)
    long countOverdueOfResponsible(@Param("responsibleId") UUID responsibleId, @Param("today") LocalDate today);

    // parcelas que vencem depois de uma data (as que somem quando o contrato e cancelado)
    List<Payment> findByContractIdAndStatusAndDueDateAfter(UUID contractId, PaymentStatus status, LocalDate date);

    // parcelas atrasadas por contrato, pra lista "meus contratos" numa consulta so: [contractId, quantidade]
    @Query("SELECT p.contract.id, COUNT(p) FROM Payment p WHERE p.contract.id IN :contractIds AND " + OVERDUE +
            " GROUP BY p.contract.id")
    List<Object[]> countOverdueOf(@Param("contractIds") Collection<UUID> contractIds, @Param("today") LocalDate today);
}

package br.com.vanroute.backend.repositories;

import java.util.List;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface StudentResponsibleRepository
        extends JpaRepository<StudentResponsible, UUID>, JpaSpecificationExecutor<StudentResponsible> {
    // danke chat

    boolean existsByResponsible_UserIdAndStudent_Id(UUID responsibleId, UUID studentId);

    // filhos de que o responsavel e admin (so esses podem ter contrato)
    @Query("SELECT sr.student FROM StudentResponsible sr WHERE sr.responsible.userId = :userId AND sr.isAdmin = true ORDER BY sr.student.name")
    List<Student> findAdminChildren(@Param("userId") UUID userId);

    // todos os responsaveis do aluno (pra notificar)
    @Query("SELECT sr.responsible.userId FROM StudentResponsible sr WHERE sr.student.id = :studentId")
    List<UUID> findResponsibleUserIds(@Param("studentId") UUID studentId);

    boolean existsByResponsible_UserIdAndStudent_IdAndIsAdminTrue(
            UUID responsibleId,
            UUID studentId);
}
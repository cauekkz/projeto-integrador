package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.student.StudentResponsible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface StudentResponsibleRepository extends JpaRepository<StudentResponsible, UUID> , JpaSpecificationExecutor<StudentResponsible> {
    //danke chat
    boolean existsByResponsible_User_CpfAndStudent_IdAndIsAdminTrue(String cpf, UUID studentId);


}
package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.student.StudentAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// a ligacao aluno -> endereco (tirar o endereco do aluno apaga so a ligacao; o endereco continua pros outros)
public interface StudentAddressRepository extends JpaRepository<StudentAddress, UUID> {

    @Query("SELECT sa FROM StudentAddress sa JOIN FETCH sa.address WHERE sa.student.id = :studentId ORDER BY sa.createdAt")
    List<StudentAddress> findOfStudent(@Param("studentId") UUID studentId);

    @Query("SELECT sa FROM StudentAddress sa JOIN FETCH sa.address JOIN FETCH sa.student " +
            "WHERE sa.student.id IN :studentIds ORDER BY sa.createdAt")
    List<StudentAddress> findOfStudents(@Param("studentIds") Collection<UUID> studentIds);

    Optional<StudentAddress> findByIdAndStudentId(UUID id, UUID studentId);

    boolean existsByStudentIdAndAddressId(UUID studentId, UUID addressId);

    long countByStudentId(UUID studentId);

    // o endereco pode ser reaproveitado por esse responsavel? (endereco de algum filho dele)
    @Query("SELECT COUNT(sa) > 0 FROM StudentAddress sa, StudentResponsible sr " +
            "WHERE sr.student = sa.student AND sr.responsible.userId = :responsibleId AND sa.address.id = :addressId")
    boolean isAddressOfResponsibleChildren(@Param("responsibleId") UUID responsibleId, @Param("addressId") UUID addressId);
}

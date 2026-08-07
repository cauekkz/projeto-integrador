package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.student.StudentResponsible;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentResponsibleRepository extends JpaRepository<StudentResponsible, UUID> {
}

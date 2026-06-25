package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.student.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {
}

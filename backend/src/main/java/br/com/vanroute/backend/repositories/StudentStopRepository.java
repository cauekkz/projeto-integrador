package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.student.StudentStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentStopRepository extends JpaRepository<StudentStop, UUID> {
    boolean existsByStudentIdAndStopId(UUID studentId, UUID stopId);
}

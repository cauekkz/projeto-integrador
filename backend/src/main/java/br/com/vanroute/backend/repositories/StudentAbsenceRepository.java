package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.trip.StudentAbsence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StudentAbsenceRepository extends JpaRepository<StudentAbsence, UUID> {
    boolean existsByStudentIdAndRouteIdAndDate(UUID studentId, UUID routeId, LocalDate date);

    List<StudentAbsence> findByRouteIdAndDate(UUID routeId, LocalDate date);

    List<StudentAbsence> findByStudentIdAndDateGreaterThanEqualOrderByDate(UUID studentId, LocalDate date);
}

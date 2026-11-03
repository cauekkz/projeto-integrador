package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.school.SchoolStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolStopRepository extends JpaRepository<SchoolStop, UUID> {
    // o primeiro ponto dessa escola na rota (a mesma escola pode aparecer em mais de um ponto)
    Optional<SchoolStop> findFirstByStopRouteIdAndSchoolIdOrderByStopOrderIndexAsc(UUID routeId, UUID schoolId);
    boolean existsByStopId(UUID stopId);
    List<SchoolStop> findByStopId(UUID stopId);
}

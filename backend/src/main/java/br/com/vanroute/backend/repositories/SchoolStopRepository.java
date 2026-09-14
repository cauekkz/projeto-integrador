package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.school.SchoolStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SchoolStopRepository extends JpaRepository<SchoolStop, UUID> {
}

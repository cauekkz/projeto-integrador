package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.school.DriverSchools;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverSchoolsRepository extends JpaRepository<DriverSchools, UUID> {
}

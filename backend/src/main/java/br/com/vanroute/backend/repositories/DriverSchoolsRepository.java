package br.com.vanroute.backend.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.vanroute.backend.models.school.DriverSchools;

public interface DriverSchoolsRepository extends JpaRepository<DriverSchools, UUID> {
    boolean existsByDriverUserIdAndSchoolId(UUID driverId, UUID schoolId);
}

package br.com.vanroute.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.vanroute.backend.models.school.DriverSchools;

public interface DriverSchoolsRepository extends JpaRepository<DriverSchools, UUID> {
    boolean existsByDriverUserIdAndSchoolId(UUID driverId, UUID schoolId);

    @Query("SELECT ds FROM DriverSchools ds JOIN FETCH ds.school s LEFT JOIN FETCH s.address " +
            "WHERE ds.driver.userId = :driverId ORDER BY s.name")
    List<DriverSchools> findByDriverWithSchool(@Param("driverId") UUID driverId);
}

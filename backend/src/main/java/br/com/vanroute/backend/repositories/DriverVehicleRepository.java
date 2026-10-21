package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.vehicle.DriverVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverVehicleRepository extends JpaRepository<DriverVehicle, UUID> {

    // vinculo ativo = sem end_date
    Optional<DriverVehicle> findFirstByDriverUserIdAndVehicleIdAndEndDateIsNull(UUID driverId, UUID vehicleId);

    @Query("SELECT dv FROM DriverVehicle dv JOIN FETCH dv.vehicle " +
            "WHERE dv.driver.userId = :driverId AND dv.endDate IS NULL ORDER BY dv.startDate")
    List<DriverVehicle> findActiveByDriver(@Param("driverId") UUID driverId);
}

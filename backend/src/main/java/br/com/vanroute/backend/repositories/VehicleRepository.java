package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.vehicle.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    Optional<Vehicle> findByRenavam(String renavam);
    Optional<Vehicle> findByPlate(String plate);
}

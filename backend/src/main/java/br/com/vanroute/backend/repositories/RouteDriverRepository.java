package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.route.RouteDriver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RouteDriverRepository extends JpaRepository<RouteDriver, UUID> {
    boolean existsByRouteIdAndDriverUserId(UUID routeId, UUID driverId);
    List<RouteDriver> findByDriverUserId(UUID driverId);
}

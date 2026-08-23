package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.route.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {
    List<RouteStop> findByRouteIdOrderByOrderIndexAsc(UUID routeId);

}

package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.route.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {
}

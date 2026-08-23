package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.route.RouteDriver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RouteDriverRepository extends JpaRepository<RouteDriver, UUID> {
}

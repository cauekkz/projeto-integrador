package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.route.RouteDriver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RouteDriverRepository extends JpaRepository<RouteDriver, UUID> {
    // motorista esta na rota hoje (quem saiu, com end_date, perde o acesso)
    boolean existsByRouteIdAndDriverUserIdAndEndDateIsNull(UUID routeId, UUID driverId);
    List<RouteDriver> findByDriverUserId(UUID driverId);

    // rotas do motorista por nome (name = "%" pra todas); activeOnly = so as que ele esta hoje
    @Query(value = "SELECT rd FROM RouteDriver rd JOIN FETCH rd.route r WHERE rd.driver.userId = :driverId " +
            "AND (:activeOnly = false OR rd.endDate IS NULL) AND LOWER(r.name) LIKE :name ORDER BY r.name",
            countQuery = "SELECT COUNT(rd) FROM RouteDriver rd JOIN rd.route r WHERE rd.driver.userId = :driverId " +
                    "AND (:activeOnly = false OR rd.endDate IS NULL) AND LOWER(r.name) LIKE :name")
    Page<RouteDriver> findPageOfDriver(@Param("driverId") UUID driverId, @Param("activeOnly") boolean activeOnly,
                                       @Param("name") String name, Pageable pageable);
    List<RouteDriver> findByRouteIdAndEndDateIsNull(UUID routeId);

    // rotas em que o motorista esta hoje
    List<RouteDriver> findByDriverUserIdAndEndDateIsNull(UUID driverId);
}

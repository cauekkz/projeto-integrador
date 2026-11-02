package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.rating.Rating;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {
    Optional<Rating> findByResponsibleUserIdAndDriverUserId(UUID responsibleId, UUID driverId);

    @EntityGraph(attributePaths = "responsible.user")
    List<Rating> findTop10ByDriverUserIdOrderByDateDesc(UUID driverId);

    // [media, quantidade]
    @Query("SELECT AVG(r.score), COUNT(r) FROM Rating r WHERE r.driver.userId = :driverId")
    List<Object[]> summaryOf(@Param("driverId") UUID driverId);
}

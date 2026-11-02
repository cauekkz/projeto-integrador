package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.group.DriverGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverGroupRepository extends JpaRepository<DriverGroup, UUID> {
    @EntityGraph(attributePaths = "students")
    List<DriverGroup> findByDriverUserIdOrderByNameAsc(UUID driverId);

    @EntityGraph(attributePaths = "students")
    Optional<DriverGroup> findByIdAndDriverUserId(UUID id, UUID driverId);

    boolean existsByDriverUserIdAndNameIgnoreCase(UUID driverId, String name);
}

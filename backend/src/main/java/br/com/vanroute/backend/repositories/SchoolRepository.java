package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.school.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SchoolRepository extends JpaRepository<School, UUID> {
    Optional<School> findByName(String name);
    Optional<School> findByAddress_Street(String addressStreet);

}

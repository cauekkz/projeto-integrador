package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.trip.Occurrence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OccurrenceRepository extends JpaRepository<Occurrence, UUID> {
    List<Occurrence> findByTripIdOrderByDateTimeDesc(UUID tripId);
}

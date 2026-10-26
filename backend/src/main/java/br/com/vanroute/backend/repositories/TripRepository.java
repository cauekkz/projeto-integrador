package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    // trip do dia que ainda vale (nao cancelada)
    Optional<Trip> findFirstByRouteIdAndDateAndStatusNot(UUID routeId, LocalDate date, TripStatus status);

    Optional<Trip> findFirstByRouteIdAndDateAndStatusIn(UUID routeId, LocalDate date, Collection<TripStatus> statuses);
}

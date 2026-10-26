package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.trip.Boarding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardingRepository extends JpaRepository<Boarding, UUID> {

    @Query("SELECT b FROM Boarding b JOIN FETCH b.student WHERE b.trip.id = :tripId")
    List<Boarding> findByTripWithStudent(@Param("tripId") UUID tripId);

    Optional<Boarding> findByTripIdAndStudentId(UUID tripId, UUID studentId);

    @Query("SELECT b FROM Boarding b JOIN FETCH b.trip t JOIN FETCH t.route " +
            "WHERE b.student.id = :studentId AND t.date = :date " +
            "AND t.status <> br.com.vanroute.backend.models.trip.enums.TripStatus.CANCELED")
    List<Boarding> findTodayOfStudent(@Param("studentId") UUID studentId, @Param("date") LocalDate date);
}

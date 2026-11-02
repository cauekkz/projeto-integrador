package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.trip.OccurrenceRequestDTO;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.trip.Boarding;
import br.com.vanroute.backend.models.trip.Occurrence;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.trip.enums.OccurrenceType;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OccurrenceServiceTest {

    @Mock private OccurrenceRepository occurrenceRepository;
    @Mock private TripRepository tripRepository;
    @Mock private BoardingRepository boardingRepository;
    @Mock private StudentResponsibleRepository studentResponsibleRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private OccurrenceService service;

    private Trip trip;
    private final UUID driverId = UUID.randomUUID();
    private final UUID parent = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setName("Carlos");
        Driver driver = new Driver();
        driver.setUserId(driverId);
        driver.setUser(user);
        Route route = new Route();
        route.setName("Ida Manha");
        trip = new Trip();
        trip.setId(UUID.randomUUID());
        trip.setDriver(driver);
        trip.setRoute(route);
        trip.setStatus(TripStatus.IN_PROGRESS);
        Student ana = new Student();
        ana.setId(UUID.randomUUID());
        Boarding boarding = new Boarding();
        boarding.setStudent(ana);
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(boardingRepository.findByTripWithStudent(trip.getId())).thenReturn(List.of(boarding));
        when(studentResponsibleRepository.findResponsibleUserIds(ana.getId())).thenReturn(List.of(parent));
        when(occurrenceRepository.save(any(Occurrence.class))).thenAnswer(inv -> {
            Occurrence occurrence = inv.getArgument(0);
            occurrence.setId(UUID.randomUUID());
            return occurrence;
        });
    }

    @Test
    void reportSavesAndWarnsTheParentsOfTheTrip() {
        var response = service.report(trip.getId(), new OccurrenceRequestDTO(OccurrenceType.TRAFFIC, " acidente na marginal "), driverId);

        assertEquals("acidente na marginal", response.description());
        verify(notificationService).send(eq(Set.of(parent)), eq(NotificationType.OCCURRENCE), eq("Ocorrência na viagem: trânsito"),
                eq("Ida Manha: acidente na marginal"), anyMap());
    }

    @Test
    void onlyTheDriverOfTheTripReports() {
        assertThrows(AccessDeniedException.class,
                () -> service.report(trip.getId(), new OccurrenceRequestDTO(OccurrenceType.OTHER, "x"), UUID.randomUUID()));
        verify(occurrenceRepository, never()).save(any());
    }

    @Test
    void notAfterTheTripEnded() {
        trip.setStatus(TripStatus.FINISHED);

        assertThrows(ResponseStatusException.class,
                () -> service.report(trip.getId(), new OccurrenceRequestDTO(OccurrenceType.OTHER, "x"), driverId));
    }

    @Test
    void parentsOfTheTripAndTheDriverCanReadOthersCannot() {
        when(occurrenceRepository.findByTripIdOrderByDateTimeDesc(trip.getId())).thenReturn(List.of());

        assertNotNull(service.list(trip.getId(), parent));
        assertNotNull(service.list(trip.getId(), driverId));
        assertThrows(AccessDeniedException.class, () -> service.list(trip.getId(), UUID.randomUUID()));
    }
}

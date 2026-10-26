package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.trip.TripLiveStateDTO;
import br.com.vanroute.backend.dtos.trip.TripLiveStopDTO;
import br.com.vanroute.backend.dtos.trip.TripStudentDTO;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import br.com.vanroute.backend.models.trip.Boarding;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.BoardingRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripNotificationServiceTest {

    @Mock private NotificationService notificationService;
    @Mock private BoardingRepository boardingRepository;
    @Mock private StudentResponsibleRepository studentResponsibleRepository;

    @InjectMocks
    private TripNotificationService service;

    private final UUID parentAna = UUID.randomUUID();   // pai da Ana e do Caio (irmaos)
    private final UUID parentBia = UUID.randomUUID();
    private final UUID ana = UUID.randomUUID(), caio = UUID.randomUUID(), bia = UUID.randomUUID();
    private Trip trip;

    @BeforeEach
    void setUp() {
        Route route = new Route();
        route.setId(UUID.randomUUID());
        route.setName("Ida");
        User user = new User();
        user.setName("Joao");
        Driver driver = new Driver();
        driver.setUser(user);
        trip = new Trip();
        trip.setId(UUID.randomUUID());
        trip.setRoute(route);
        trip.setDriver(driver);
        trip.setStatus(TripStatus.STARTED);
        when(studentResponsibleRepository.findResponsibleUserIds(ana)).thenReturn(List.of(parentAna));
        when(studentResponsibleRepository.findResponsibleUserIds(caio)).thenReturn(List.of(parentAna));
        when(studentResponsibleRepository.findResponsibleUserIds(bia)).thenReturn(List.of(parentBia));
    }

    private static TripStudentDTO st(UUID id, String name, StudentStopAction action, StudentStopLocationType type, BoardingStatus status) {
        return new TripStudentDTO(id, name, action, type, status, null, null);
    }

    private static TripLiveStopDTO stop(UUID id, boolean done, Double eta, TripStudentDTO... students) {
        return new TripLiveStopDTO(id, 1, -23.5, -46.6, List.of(students), List.of(), done, null, eta);
    }

    private static TripLiveStateDTO state(TripLiveStopDTO... stops) {
        return new TripLiveStateDTO(UUID.randomUUID(), "Ida", TripStatus.STARTED, null, null, null, null, null, null, null, List.of(stops));
    }

    @Test
    void siblingsAtTheNextStopBecomeOneNotificationForTheirParent() {
        UUID home = UUID.randomUUID();
        TripLiveStateDTO after = state(stop(home, false, 300.0,
                st(ana, "Ana", StudentStopAction.BOARDING, StudentStopLocationType.HOME, BoardingStatus.PENDING),
                st(caio, "Caio", StudentStopAction.BOARDING, StudentStopLocationType.HOME, BoardingStatus.PENDING)));

        service.nextStopChanged(trip, null, after);

        verify(notificationService).send(eq(List.of(parentAna)), eq(NotificationType.VAN_ON_THE_WAY),
                eq("A van está indo buscar Ana e Caio"), contains("5 min"), anyMap());
        verify(notificationService, times(1)).send(any(), any(), any(), any(), any());
    }

    @Test
    void etaDiscountsTheTimeSinceTheLastCalculation() {
        // calculado ha 2 min com 10 min ate o ponto: faltam 8
        TripLiveStateDTO after = new TripLiveStateDTO(UUID.randomUUID(), "Ida", TripStatus.STARTED, null, null, null,
                System.currentTimeMillis() - 120_000, null, null, null,
                List.of(stop(UUID.randomUUID(), false, 600.0,
                        st(ana, "Ana", StudentStopAction.BOARDING, StudentStopLocationType.HOME, BoardingStatus.PENDING))));

        service.nextStopChanged(trip, null, after);

        verify(notificationService).send(eq(List.of(parentAna)), eq(NotificationType.VAN_ON_THE_WAY), anyString(),
                contains("8 min"), anyMap());
    }

    @Test
    void nothingIsSentWhileTheNextStopIsTheSame() {
        UUID home = UUID.randomUUID();
        TripLiveStopDTO same = stop(home, false, 120.0,
                st(ana, "Ana", StudentStopAction.BOARDING, StudentStopLocationType.HOME, BoardingStatus.PENDING));

        service.nextStopChanged(trip, state(same), state(same));

        verifyNoInteractions(notificationService);
    }

    @Test
    void goingToSchoolWarnsOnlyWhoIsStillOnTheVan() {
        UUID home = UUID.randomUUID(), school = UUID.randomUUID();
        TripLiveStateDTO before = state(
                stop(home, false, 60.0, st(bia, "Bia", StudentStopAction.BOARDING, StudentStopLocationType.HOME, BoardingStatus.PENDING)),
                stop(school, false, 600.0,
                        st(ana, "Ana", StudentStopAction.ALIGHTING, StudentStopLocationType.SCHOOL, BoardingStatus.BOARDED),
                        st(bia, "Bia", StudentStopAction.ALIGHTING, StudentStopLocationType.SCHOOL, BoardingStatus.PENDING)));
        // Bia nao apareceu: a van vai direto pra escola e so a Ana desce la
        TripLiveStateDTO after = state(
                stop(home, true, null, st(bia, "Bia", StudentStopAction.BOARDING, StudentStopLocationType.HOME, BoardingStatus.ABSENT)),
                stop(school, false, 540.0,
                        st(ana, "Ana", StudentStopAction.ALIGHTING, StudentStopLocationType.SCHOOL, BoardingStatus.BOARDED),
                        st(bia, "Bia", StudentStopAction.ALIGHTING, StudentStopLocationType.SCHOOL, BoardingStatus.ABSENT)));

        service.nextStopChanged(trip, before, after);

        verify(notificationService).send(eq(List.of(parentAna)), eq(NotificationType.VAN_ON_THE_WAY),
                eq("Ana está a caminho da escola"), anyString(), anyMap());
        verify(notificationService, never()).send(eq(List.of(parentBia)), any(), any(), any(), any());
    }

    @Test
    void finishedTripDoesNotWarn() {
        trip.setStatus(TripStatus.FINISHED);
        service.nextStopChanged(trip, null, state(stop(UUID.randomUUID(), false, 60.0,
                st(ana, "Ana", StudentStopAction.BOARDING, StudentStopLocationType.HOME, BoardingStatus.PENDING))));
        verifyNoInteractions(notificationService);
    }

    @Test
    void tripStartedSkipsStudentsAnnouncedAbsent() {
        Student a = new Student(); a.setId(ana); a.setName("Ana");
        Student b = new Student(); b.setId(bia); b.setName("Bia");
        Boarding goes = new Boarding(); goes.setStudent(a); goes.setStatus(BoardingStatus.PENDING);
        Boarding absent = new Boarding(); absent.setStudent(b); absent.setStatus(BoardingStatus.ABSENT);
        when(boardingRepository.findByTripWithStudent(trip.getId())).thenReturn(List.of(goes, absent));

        service.tripStarted(trip, false);

        verify(notificationService).send(eq(List.of(parentAna)), eq(NotificationType.TRIP_STARTED), eq("A van saiu"),
                eq("Joao iniciou a rota Ida (Ana)"), anyMap());
        verify(notificationService, never()).send(eq(List.of(parentBia)), any(), any(), any(), any());
    }

    @Test
    void deliveredSaysWhereTheStudentGotOff() {
        Student a = new Student(); a.setId(ana); a.setName("Ana");
        TripLiveStateDTO after = state(stop(UUID.randomUUID(), true, null,
                st(ana, "Ana", StudentStopAction.ALIGHTING, StudentStopLocationType.SCHOOL, BoardingStatus.DELIVERED)));

        service.delivered(trip, a, after);

        verify(notificationService).send(eq(List.of(parentAna)), eq(NotificationType.STUDENT_DELIVERED),
                eq("Ana chegou na escola"), anyString(), anyMap());
    }
}

package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.dtos.trip.AbsenceRequestDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.chat.Chat;
import br.com.vanroute.backend.models.chat.enums.MessageType;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteDriver;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.trip.StudentAbsence;
import br.com.vanroute.backend.models.user.Driver;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentAbsenceServiceTest {

    @Mock private StudentAbsenceRepository studentAbsenceRepository;
    @Mock private StudentResponsibleRepository studentResponsibleRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentStopRepository studentStopRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private RouteDriverRepository routeDriverRepository;
    @Mock private UserRepository userRepository;
    @Mock private TripService tripService;
    @Mock private ChatService chatService;
    @Mock private ChatMessageService chatMessageService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private StudentAbsenceService service;

    private final UUID parentId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(TripService.ZONE);
    private Student student;
    private Route route;
    private Driver driver;

    @BeforeEach
    void setUp() {
        student = new Student();
        student.setId(UUID.randomUUID());
        student.setName("Ana");
        route = new Route();
        route.setId(UUID.randomUUID());
        route.setName("Volta");
        driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        RouteDriver routeDriver = new RouteDriver();
        routeDriver.setDriver(driver);

        when(studentResponsibleRepository.existsByResponsible_UserIdAndStudent_Id(parentId, student.getId())).thenReturn(true);
        when(routeRepository.findById(route.getId())).thenReturn(Optional.of(route));
        when(studentStopRepository.findRoutesOfStudent(student.getId())).thenReturn(List.of(route));
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(studentAbsenceRepository.save(any(StudentAbsence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(routeDriverRepository.findByRouteIdAndEndDateIsNull(route.getId())).thenReturn(List.of(routeDriver));
        Chat chat = new Chat();
        chat.setId(UUID.randomUUID());
        when(chatService.createOrGetChat(driver.getUserId(), parentId)).thenReturn(chat);
    }

    @Test
    void parentAnnouncesAbsenceTheStudentLeavesTheTripAndTheDriverIsWarned() {
        service.create(student.getId(), new AbsenceRequestDTO(route.getId(), today), parentId);

        verify(tripService).checkAbsenceAllowed(route.getId(), student.getId(), today);
        verify(tripService).applyAbsence(any(StudentAbsence.class));
        verify(chatMessageService).sendSystemMessage(any(), eq(parentId), contains("Ana não vai na rota Volta"), eq(MessageType.ABSENCE), anyString());
        verify(chatMessageService).publish(any(), any());
        verify(notificationService).send(anyCollection(), eq(NotificationType.ABSENCE_REPORTED), eq("Falta avisada"),
                contains("Ana não vai na rota Volta"), anyMap());
    }

    @Test
    void onlyTheStudentsResponsibleCanAnnounce() {
        assertThrows(AccessDeniedException.class,
                () -> service.create(student.getId(), new AbsenceRequestDTO(route.getId(), today), UUID.randomUUID()));
        verify(studentAbsenceRepository, never()).save(any());
    }

    @Test
    void pastDateIsRejected() {
        assertThrows(BadRequestException.class,
                () -> service.create(student.getId(), new AbsenceRequestDTO(route.getId(), today.minusDays(1)), parentId));
    }

    @Test
    void routeMustBeOneOfTheStudentsRoutes() {
        when(studentStopRepository.findRoutesOfStudent(student.getId())).thenReturn(List.of());

        assertThrows(BadRequestException.class,
                () -> service.create(student.getId(), new AbsenceRequestDTO(route.getId(), today), parentId));
    }
}

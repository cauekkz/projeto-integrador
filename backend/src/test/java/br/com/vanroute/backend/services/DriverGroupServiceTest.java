package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.group.DriverGroupRequestDTO;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.group.DriverGroup;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.student.Student;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DriverGroupServiceTest {

    @Mock private DriverGroupRepository driverGroupRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentResponsibleRepository studentResponsibleRepository;
    @Mock private UserDriverContractRepository userDriverContractRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private DriverGroupService service;

    private final UUID driverId = UUID.randomUUID();
    private DriverGroup group;
    private Student ana;
    private Student pedro;

    private static Student student(String name) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setName(name);
        return student;
    }

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setName("Carlos");
        Driver driver = new Driver();
        driver.setUserId(driverId);
        driver.setUser(user);
        group = new DriverGroup();
        group.setId(UUID.randomUUID());
        group.setDriver(driver);
        group.setName("Escola X manha");
        ana = student("Ana");
        pedro = student("Pedro");
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverGroupRepository.findByIdAndDriverUserId(any(), eq(driverId))).thenReturn(Optional.of(group));
        when(driverGroupRepository.save(any(DriverGroup.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void groupNamesAreUniquePerDriver() {
        when(driverGroupRepository.existsByDriverUserIdAndNameIgnoreCase(driverId, "Escola X manha")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> service.create(new DriverGroupRequestDTO(" Escola X manha "), driverId));
        verify(driverGroupRepository, never()).save(any());
    }

    @Test
    void onlyStudentsWithAnActiveContractEnterTheGroup() {
        when(userDriverContractRepository.findFirstByDriverUserIdAndStudentIdAndContractStatus(driverId, ana.getId(), ContractStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.addStudent(UUID.randomUUID(), ana.getId(), driverId));
        assertTrue(group.getStudents().isEmpty());
    }

    @Test
    void theSameStudentTwiceIsRefused() {
        group.getStudents().add(ana);
        when(userDriverContractRepository.findFirstByDriverUserIdAndStudentIdAndContractStatus(driverId, ana.getId(), ContractStatus.ACTIVE))
                .thenReturn(Optional.of(new UserDriverContract()));

        assertThrows(ResponseStatusException.class, () -> service.addStudent(UUID.randomUUID(), ana.getId(), driverId));
    }

    @Test
    void noticeGoesOncePerParentWithTheirChildrenAndTheOptionalReason() {
        group.getStudents().addAll(List.of(ana, pedro));
        UUID mom = UUID.randomUUID();
        UUID otherParent = UUID.randomUUID();
        // a mae tem os dois no grupo; o outro responsavel so o Pedro
        when(studentResponsibleRepository.findResponsibleUserIds(ana.getId())).thenReturn(List.of(mom));
        when(studentResponsibleRepository.findResponsibleUserIds(pedro.getId())).thenReturn(List.of(mom, otherParent));

        assertEquals(2, service.notice(UUID.randomUUID(), "pneu furado", driverId).notifiedResponsibles());

        verify(notificationService).send(eq(List.of(mom)), eq(NotificationType.DRIVER_NOTICE), eq("Aviso do motorista"),
                eq("Carlos avisou que não vai buscar Ana e Pedro hoje. Motivo: pneu furado"), anyMap());
        verify(notificationService).send(eq(List.of(otherParent)), eq(NotificationType.DRIVER_NOTICE), eq("Aviso do motorista"),
                eq("Carlos avisou que não vai buscar Pedro hoje. Motivo: pneu furado"), anyMap());
    }

    @Test
    void noticeWithoutReasonKeepsTheDefaultMessage() {
        group.getStudents().add(ana);
        when(studentResponsibleRepository.findResponsibleUserIds(ana.getId())).thenReturn(List.of(UUID.randomUUID()));

        service.notice(UUID.randomUUID(), "  ", driverId);

        verify(notificationService).send(anyCollection(), any(), any(), eq("Carlos avisou que não vai buscar Ana hoje."), anyMap());
    }

    @Test
    void emptyGroupHasNobodyToWarn() {
        assertThrows(ResponseStatusException.class, () -> service.notice(UUID.randomUUID(), null, driverId));
        verifyNoInteractions(notificationService);
    }

    @Test
    void namesAreJoinedLikeInPortuguese() {
        assertEquals("Ana, Bia e Pedro", DriverGroupService.names(List.of(student("Ana"), student("Bia"), student("Pedro"))));
    }
}

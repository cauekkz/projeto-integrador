package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.ResponsibleProfileResponseDTO;
import br.com.vanroute.backend.models.contract.Contract;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;
import br.com.vanroute.backend.repositories.ChatRepository;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.PaymentRepository;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResponsibleProfileServiceTest {

    @Mock
    private ResponsibleRepository responsibleRepository;
    @Mock
    private DriverRepository driverRepository;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private UserDriverContractRepository userDriverContractRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private br.com.vanroute.backend.repositories.StudentResponsibleRepository studentResponsibleRepository;

    @InjectMocks
    private ResponsibleProfileService service;

    private final UUID driverId = UUID.randomUUID();
    private final UUID otherDriverId = UUID.randomUUID();
    private final UUID responsibleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setName("Maria");
        user.setPhone("11999999999");
        Responsible responsible = new Responsible();
        responsible.setUserId(responsibleId);
        responsible.setUser(user);
        when(responsibleRepository.findById(responsibleId)).thenReturn(Optional.of(responsible));
        when(driverRepository.existsById(driverId)).thenReturn(true);
        when(userDriverContractRepository.findAllOfUser(responsibleId))
                .thenReturn(List.of(contract(driverId, "Ana"), contract(otherDriverId, "Caio")));
    }

    private static UserDriverContract contract(UUID driverId, String studentName) {
        Driver driver = new Driver();
        driver.setUserId(driverId);
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setName(studentName);
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setStatus(ContractStatus.ACTIVE);
        UserDriverContract udc = new UserDriverContract();
        udc.setDriver(driver);
        udc.setStudent(student);
        udc.setContract(contract);
        return udc;
    }

    @Test
    void driverWithAChatSeesNamePhoneAndOnlyTheChildrenWithHim() {
        when(chatRepository.existsBetween(driverId, responsibleId)).thenReturn(true);

        ResponsibleProfileResponseDTO profile = service.profile(responsibleId, driverId);

        assertEquals("Maria", profile.name());
        assertEquals("11999999999", profile.phone());
        assertEquals(FinancialStatus.UP_TO_DATE, profile.financialStatus());
        assertEquals(List.of("Ana"), profile.students().stream().map(ResponsibleProfileResponseDTO.StudentItem::studentName).toList());
    }

    @Test
    void debtWithAnotherDriverDoesNotShow() {
        when(userDriverContractRepository.existsByDriverUserIdAndResponsibleUserId(driverId, responsibleId)).thenReturn(true);
        // atrasado so com outro motorista: pra esse motorista ele esta em dia
        lenient().when(paymentRepository.countOverdueOfResponsible(eq(responsibleId), any())).thenReturn(3L);
        when(paymentRepository.countOverdueBetween(eq(driverId), eq(responsibleId), any())).thenReturn(0L);

        ResponsibleProfileResponseDTO profile = service.profile(responsibleId, driverId);

        assertEquals(FinancialStatus.UP_TO_DATE, profile.financialStatus());
        assertEquals(0L, profile.overduePayments());
    }

    @Test
    void debtWithThisDriverShows() {
        when(userDriverContractRepository.existsByDriverUserIdAndResponsibleUserId(driverId, responsibleId)).thenReturn(true);
        when(paymentRepository.countOverdueBetween(eq(driverId), eq(responsibleId), any())).thenReturn(2L);

        ResponsibleProfileResponseDTO profile = service.profile(responsibleId, driverId);

        assertEquals(FinancialStatus.OVERDUE, profile.financialStatus());
        assertEquals(2L, profile.overduePayments());
    }

    @Test
    void eachChildComesWithTheLinkLabel() {
        when(chatRepository.existsBetween(driverId, responsibleId)).thenReturn(true);
        br.com.vanroute.backend.models.student.StudentResponsible link = new br.com.vanroute.backend.models.student.StudentResponsible();
        link.setRelationType(br.com.vanroute.backend.models.student.enums.RelationType.LEGAL);
        link.setAdmin(true);
        when(studentResponsibleRepository.findByResponsible_UserIdAndStudent_Id(eq(responsibleId), any())).thenReturn(Optional.of(link));

        ResponsibleProfileResponseDTO.StudentItem child = service.profile(responsibleId, driverId).students().get(0);

        assertEquals(br.com.vanroute.backend.models.student.enums.RelationType.LEGAL, child.relationType());
        assertEquals(true, child.admin());
    }

    @Test
    void theResponsibleSeesTheirOwnProfileWithAllContracts() {
        ResponsibleProfileResponseDTO profile = service.profile(responsibleId, responsibleId);

        assertEquals(List.of("Ana", "Caio"), profile.students().stream().map(ResponsibleProfileResponseDTO.StudentItem::studentName).toList());
    }

    @Test
    void onlyADriverWithChatOrContractSeesTheProfile() {
        assertThrows(AccessDeniedException.class, () -> service.profile(responsibleId, driverId));
        assertThrows(AccessDeniedException.class, () -> service.profile(responsibleId, UUID.randomUUID()));
    }
}

package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.student.StudentAddress;
import java.util.UUID;
import java.util.List;
import br.com.vanroute.backend.dtos.student.ChildSummaryResponseDTO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.dtos.user.StudentRequestDTO;
import br.com.vanroute.backend.mapper.StudentResponsibleMapper;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.repositories.StudentAddressRepository;
import br.com.vanroute.backend.dtos.student.AllStudentsFilterRequestDTO;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.models.student.enums.RelationType;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.repositories.StudentRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;

@ExtendWith(MockitoExtension.class)
public class StudentServiceTest {

    private static final UUID RESPONSIBLE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ResponsibleRepository responsibleRepository;

    @Mock
    private StudentResponsibleRepository studentResponsibleRepository;

    @Mock
    private AddressService addressService;

    @Mock
    private StudentAddressRepository studentAddressRepository;

    @Mock
    private MapService mapService;

    @Spy
    private StudentResponsibleMapper studentResponsibleMapper;

    @Mock
    private ChatService chatService;

    @Mock
    private br.com.vanroute.backend.repositories.DriverRepository driverRepository;

    @Mock
    private br.com.vanroute.backend.repositories.StudentStopRepository studentStopRepository;

    @Mock
    private StudentAddressService studentAddressService;

    @InjectMocks
    private StudentService studentService;

    private Responsible responsible;

    @BeforeEach
    void setUp() {
        responsible = new Responsible();
        User u = new User();
        u.setCpf("12345678901");
        responsible.setUser(u);
    }

    @Test
    void testCreateStudentWithRelation_PersistsAdminRelation() {
        AddressRequestDTO addressDto = new AddressRequestDTO("Rua Teste", "01000-000", "São Paulo", "Centro", 10, "SP", null, null);
        StudentRequestDTO requestDTO = new StudentRequestDTO("John Doe", "Notes", LocalDate.of(2015, 1, 1), RelationType.FINANCIAL, addressDto, null, null, null);
        Address address = new Address();
        address.setStreet("Rua Teste");
        responsible.setAddress(address);

        // o endereco vira o "Casa" do aluno (regras do endereco no StudentAddressServiceTest)
        when(studentAddressService.resolve(addressDto, null, RESPONSIBLE_ID)).thenReturn(address);
        when(studentAddressService.attach(any(), eq(address), any())).thenAnswer(inv -> {
            StudentAddress sa = new StudentAddress();
            sa.setAddress(address);
            sa.setLabel("Casa");
            return sa;
        });
        when(responsibleRepository.findById(RESPONSIBLE_ID)).thenReturn(Optional.of(responsible));

        studentService.createStudentWithRelation(requestDTO, RESPONSIBLE_ID);

        ArgumentCaptor<StudentResponsible> captor = ArgumentCaptor.forClass(StudentResponsible.class);
        verify(studentResponsibleRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().isAdmin());
        assertEquals(RelationType.FINANCIAL, captor.getValue().getRelationType());
        assertEquals(responsible, captor.getValue().getResponsible());
        verify(studentAddressService).attach(any(), eq(address), any());
    }

    @Test
    void testCreateStudentWithRelation_ResponsibleNotFound() {
        AddressRequestDTO addressDto = new AddressRequestDTO("Rua Teste", "01000-000", "São Paulo", "Centro", 10, "SP", null, null);
        StudentRequestDTO requestDTO = new StudentRequestDTO("John", "Notes", LocalDate.of(2015, 1, 1), RelationType.LEGAL, addressDto, null, null, null);

        when(studentAddressService.resolve(addressDto, null, OTHER_ID)).thenReturn(new Address());
        when(studentAddressService.attach(any(), any(), any())).thenReturn(new StudentAddress());
        when(responsibleRepository.findById(OTHER_ID)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> studentService.createStudentWithRelation(requestDTO, OTHER_ID));

        assertEquals("Responsible not found", exception.getMessage());
        verify(studentResponsibleRepository, never()).save(any());
    }

    @Test
    void testGetAllStudentsResponsible_Success() {
        AllStudentsFilterRequestDTO filter = new AllStudentsFilterRequestDTO(null, null, null);
        PageRequest pageable = PageRequest.of(0, 10);
        
        Student student = new Student();
        student.setName("Student 1");
        
        StudentResponsible sr = new StudentResponsible();
        sr.setStudent(student);
        sr.setResponsible(responsible);
        
        Page<StudentResponsible> page = new PageImpl<>(Collections.singletonList(sr));
        
        when(studentResponsibleRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        
        Page<Student> resultPage = studentService.getAllStudentsResponsible(filter, pageable, RESPONSIBLE_ID);
        
        assertNotNull(resultPage);
        assertEquals(1, resultPage.getContent().size());
        assertEquals("Student 1", resultPage.getContent().get(0).getName());
    }

    private br.com.vanroute.backend.models.chat.Chat chatBetween(User driverUser, User parentUser) {
        br.com.vanroute.backend.models.chat.Chat chat = new br.com.vanroute.backend.models.chat.Chat();
        chat.setUserOne(driverUser);
        chat.setUserTwo(parentUser);
        return chat;
    }

    @Test
    void driverSeesAdminChildrenOfTheChatPartner() {
        UUID chatId = UUID.randomUUID();
        User driverUser = new User();
        driverUser.setId(UUID.randomUUID());
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setCpf("222");
        Student child = new Student();
        child.setId(UUID.randomUUID());
        child.setName("Ana");
        when(driverRepository.existsById(driverUser.getId())).thenReturn(true);
        when(chatService.getChatById(chatId)).thenReturn(chatBetween(driverUser, parentUser));
        when(studentResponsibleRepository.findAdminChildren(parentUser.getId())).thenReturn(List.of(child));

        List<ChildSummaryResponseDTO> children = studentService.getChildrenOfChatPartner(chatId, driverUser.getId());

        assertEquals(1, children.size());
        assertEquals("Ana", children.get(0).name());
        verify(chatService).validateChatAccess(chatId, driverUser.getId());
    }

    @Test
    void responsibleCannotListChildrenThroughTheChat() {
        UUID chatId = UUID.randomUUID();
        when(driverRepository.existsById(OTHER_ID)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> studentService.getChildrenOfChatPartner(chatId, OTHER_ID));
        verify(studentResponsibleRepository, never()).findAdminChildren(any());
    }
}

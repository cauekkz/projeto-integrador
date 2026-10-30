package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.dtos.student.StudentAddressRequestDTO;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.chat.Chat;
import br.com.vanroute.backend.dtos.student.ChildSummaryResponseDTO;
import java.util.UUID;

import java.util.List;

import br.com.vanroute.backend.repositories.StudentStopRepository;

import br.com.vanroute.backend.dtos.route.StudentRouteResponse;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.transaction.annotation.Transactional;

import br.com.vanroute.backend.dtos.student.StudentResponsibleResponseDTO;
import br.com.vanroute.backend.mapper.StudentResponsibleMapper;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.student.StudentAddress;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import br.com.vanroute.backend.dtos.user.StudentRequestDTO;
import br.com.vanroute.backend.dtos.student.AllStudentsFilterRequestDTO;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.repositories.StudentRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.specifications.student.StudentResponsibleSpecification;

import org.springframework.data.domain.Page;
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final ResponsibleRepository responsibleRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;
    private final StudentResponsibleMapper studentResponsibleMapper;
    private final StudentStopRepository studentStopRepository;
    private final ChatService chatService;
    private final DriverRepository driverRepository;
    private final StudentAddressService studentAddressService;

    public StudentService(StudentRepository studentRepository, ResponsibleRepository responsibleRepository, StudentResponsibleRepository studentResponsibleRepository,StudentResponsibleMapper studentResponsibleMapper, StudentStopRepository studentStopRepository, ChatService chatService, DriverRepository driverRepository, StudentAddressService studentAddressService) {
        this.studentRepository = studentRepository;
        this.responsibleRepository = responsibleRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.studentResponsibleMapper = studentResponsibleMapper;
        this.studentStopRepository = studentStopRepository;
        this.chatService = chatService;
        this.driverRepository = driverRepository;
        this.studentAddressService = studentAddressService;
    }

        @Transactional
        public StudentResponsibleResponseDTO createStudentWithRelation(StudentRequestDTO dto, UUID responsibleId){
            Student student = new Student();
            student.setName(dto.name());
            student.setNotes(dto.notes());
            student.setBirthDate(dto.birthDate());
            // um endereco (address/addressId) ou varios (addresses). Resolve todos antes de salvar o aluno:
            // se algum for invalido, nada fica gravado
            List<StudentAddressRequestDTO> requested = dto.addresses() != null && !dto.addresses().isEmpty()
                    ? dto.addresses()
                    : List.of(new StudentAddressRequestDTO(dto.addressLabel(), dto.address(), dto.addressId()));
            List<Address> addresses = requested.stream()
                    .map(request -> studentAddressService.resolve(request.address(), request.addressId(), responsibleId))
                    .toList();
            if (addresses.stream().map(Address::getId).distinct().count() < addresses.size()) {
                throw new BadRequestException("O mesmo endereço apareceu duas vezes");
            }
            studentRepository.save(student);
            StudentAddress studentAddress = null;
            for (int i = 0; i < addresses.size(); i++) {
                String label = requested.get(i).label();
                StudentAddress attached = studentAddressService.attach(student, addresses.get(i),
                        label == null || label.isBlank() ? (i == 0 ? null : "Endereço " + (i + 1)) : label);
                if (studentAddress == null) {
                    studentAddress = attached;
                }
            }

            StudentResponsible studentResponsible = new StudentResponsible();
            studentResponsible.setStudent(student);
            studentResponsible.setRelationType(dto.relationType());
            studentResponsible.setAdmin(true);

            //obrigado felipe já ia fica maluco
            Responsible responsible = responsibleRepository.findById(responsibleId)
                    .orElseThrow(() -> new AccessDeniedException("Responsible not found"));
            studentResponsible.setResponsible(responsible);
            studentResponsibleRepository.save(studentResponsible);
            return studentResponsibleMapper.toResponse(
                    student,
                    studentAddress,
                    responsible
            );
        }    

        public Page<Student> getAllStudentsResponsible(AllStudentsFilterRequestDTO filter,Pageable pageable,UUID responsibleId) {
            Specification<StudentResponsible> specification = StudentResponsibleSpecification.withFilters(responsibleId, filter);
            Page<StudentResponsible> result = studentResponsibleRepository.findAll(specification, pageable);
            return result.map(StudentResponsible::getStudent);
        }


    //ns se a melhor coisa é fazer isso nesse service mas fds nao consigo pensa num service diferente pra isso, talvez um especifico pra isso mas nao sei

    // responsavel ve so o nome das rotas em que o filho esta (sem pontos nem ordem)
    public List<StudentRouteResponse> getStudentRoutes(UUID studentId, UUID responsibleId) {
        if (!studentResponsibleRepository.existsByResponsible_UserIdAndStudent_Id(responsibleId, studentId)) {
            throw new AccessDeniedException("Responsável não vinculado a esse aluno");
        }
        return studentStopRepository.findRoutesOfStudent(studentId).stream()
                .map(route -> new StudentRouteResponse(route.getId(), route.getName()))
                .toList();
    }

    // botao "ver filhos" do chat: so o motorista que participa do chat ve os filhos do pai da conversa
    @Transactional(readOnly = true)
    public List<ChildSummaryResponseDTO> getChildrenOfChatPartner(UUID chatId, UUID driverId) {
        chatService.validateChatAccess(chatId, driverId);
        if (!driverRepository.existsById(driverId)) {
            throw new AccessDeniedException("Só o motorista do chat pode ver os filhos");
        }
        Chat chat = chatService.getChatById(chatId);
        User other = chat.getUserOne().getId().equals(driverId) ? chat.getUserTwo() : chat.getUserOne();
        return studentResponsibleRepository.findAdminChildren(other.getId()).stream()
                .map(student -> new ChildSummaryResponseDTO(student.getId(), student.getName()))
                .toList();
    }
}

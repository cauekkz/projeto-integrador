package br.com.vanroute.backend.services;

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

import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.dtos.student.StudentResponsibleResponseDTO;
import br.com.vanroute.backend.mapper.StudentResponsibleMapper;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.student.StudentAddress;
import br.com.vanroute.backend.repositories.StudentAddressRepository;
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
    private final AddressService addressService;
    private final StudentAddressRepository studentAddressRepository;
    private final StudentResponsibleMapper studentResponsibleMapper;
    private final MapService mapService;
    private final StudentStopRepository studentStopRepository;
    private final ChatService chatService;
    private final DriverRepository driverRepository;

    public StudentService(StudentRepository studentRepository, ResponsibleRepository responsibleRepository, StudentResponsibleRepository studentResponsibleRepository, AddressService addressService, StudentAddressRepository studentAddressRepository,StudentResponsibleMapper studentResponsibleMapper, MapService mapService, StudentStopRepository studentStopRepository, ChatService chatService, DriverRepository driverRepository) {
        this.studentRepository = studentRepository;
        this.responsibleRepository = responsibleRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.addressService = addressService;
        this.studentAddressRepository = studentAddressRepository;
        this.studentResponsibleMapper = studentResponsibleMapper;
        this.mapService = mapService;
        this.studentStopRepository = studentStopRepository;
        this.chatService = chatService;
        this.driverRepository = driverRepository;
    }

        @Transactional
        public StudentResponsibleResponseDTO createStudentWithRelation(StudentRequestDTO dto, UUID responsibleId){
            Student student = new Student();
            student.setName(dto.name());
            student.setNotes(dto.notes());
            student.setBirthDate(dto.birthDate());
            Address address = addressService.addAddress(dto.address());
            Coordenadas cords = mapService.searchCoord(address);
            address.setLatitude(cords.latitude());
            address.setLongitude(cords.longitude());
            studentRepository.save(student);
            StudentAddress studentAddress = new StudentAddress();
            studentAddress.setAddress(address);
            studentAddress.setStudent(student);
            studentAddressRepository.save(studentAddress);

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

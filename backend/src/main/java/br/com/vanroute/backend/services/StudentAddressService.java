package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;
import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.dtos.student.StudentAddressRequestDTO;
import br.com.vanroute.backend.dtos.student.StudentPlaceResponseDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentAddress;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.repositories.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

// enderecos do aluno = onde ele e buscado/deixado (pode ter varios). Quem cadastra e o responsavel; o motorista so
// escolhe entre eles ao criar o ponto da rota (nao digita endereco). O endereco do responsavel e so o de cobranca.
@Service
public class StudentAddressService {

    private static final Set<TripStatus> ACTIVE_TRIP = EnumSet.of(TripStatus.STARTED, TripStatus.IN_PROGRESS, TripStatus.PAUSED);
    private static final String DEFAULT_LABEL = "Casa";

    private final StudentAddressRepository studentAddressRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;
    private final StudentRepository studentRepository;
    private final StudentStopRepository studentStopRepository;
    private final ResponsibleRepository responsibleRepository;
    private final AddressRepository addressRepository;
    private final RouteDriverRepository routeDriverRepository;
    private final TripRepository tripRepository;
    private final AddressService addressService;
    private final NotificationService notificationService;

    public StudentAddressService(StudentAddressRepository studentAddressRepository, StudentResponsibleRepository studentResponsibleRepository,
                                 StudentRepository studentRepository, StudentStopRepository studentStopRepository,
                                 ResponsibleRepository responsibleRepository, AddressRepository addressRepository,
                                 RouteDriverRepository routeDriverRepository, TripRepository tripRepository,
                                 AddressService addressService, NotificationService notificationService) {
        this.studentAddressRepository = studentAddressRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.studentRepository = studentRepository;
        this.studentStopRepository = studentStopRepository;
        this.responsibleRepository = responsibleRepository;
        this.addressRepository = addressRepository;
        this.routeDriverRepository = routeDriverRepository;
        this.tripRepository = tripRepository;
        this.addressService = addressService;
        this.notificationService = notificationService;
    }

    public List<StudentPlaceResponseDTO> list(UUID studentId, UUID responsibleId) {
        checkResponsible(studentId, responsibleId);
        return studentAddressRepository.findOfStudent(studentId).stream().map(StudentAddressService::toResponse).toList();
    }

    @Transactional
    public StudentPlaceResponseDTO add(UUID studentId, StudentAddressRequestDTO request, UUID responsibleId) {
        checkResponsible(studentId, responsibleId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        return toResponse(attach(student, resolve(request.address(), request.addressId(), responsibleId), request.label()));
    }

    // usado tambem no cadastro do aluno (o primeiro endereco vira "Casa")
    StudentAddress attach(Student student, Address address, String label) {
        if (studentAddressRepository.existsByStudentIdAndAddressId(student.getId(), address.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O aluno já tem esse endereço");
        }
        StudentAddress studentAddress = new StudentAddress();
        studentAddress.setStudent(student);
        studentAddress.setAddress(address);
        studentAddress.setLabel(label == null || label.isBlank() ? DEFAULT_LABEL : label.trim());
        return studentAddressRepository.save(studentAddress);
    }

    // endereco novo ou reaproveitado: de um filho desse responsavel (irmaos na mesma casa) ou o de cobranca dele.
    // endereco novo que ja existe no banco (mesma rua/numero/cep) tambem so liga: nao duplica nem chama a API de mapas
    Address resolve(AddressRequestDTO newAddress, UUID addressId, UUID responsibleId) {
        if ((newAddress == null) == (addressId == null)) {
            throw new BadRequestException("Mande um endereço novo (address) ou um endereço que já existe (addressId)");
        }
        if (addressId != null) {
            boolean billing = responsibleRepository.findById(responsibleId)
                    .map(r -> r.getAddress() != null && r.getAddress().getId().equals(addressId)).orElse(false);
            if (!billing && !studentAddressRepository.isAddressOfResponsibleChildren(responsibleId, addressId)) {
                throw new AccessDeniedException("Esse endereço não é seu nem de um filho seu");
            }
            return addressRepository.findById(addressId).orElseThrow(() -> new EntityNotFoundException("Address not found"));
        }
        return addressService.findOrCreate(newAddress);
    }

    // tira o endereco do aluno: apaga so a ligacao (o endereco continua pra quem mais usa, ex.: o irmao).
    // o aluno sai dos pontos de casa que usam ele (a rota fica com pendencia) e o motorista e avisado
    @Transactional
    public void remove(UUID studentId, UUID studentAddressId, UUID responsibleId) {
        checkResponsible(studentId, responsibleId);
        StudentAddress studentAddress = studentAddressRepository.findByIdAndStudentId(studentAddressId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("Endereço não encontrado"));
        if (studentAddressRepository.countByStudentId(studentId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O aluno precisa de pelo menos um endereço");
        }
        List<StudentStop> stops = studentStopRepository.findByStudentIdAndStopAddressId(studentId, studentAddress.getAddress().getId()).stream()
                .filter(stop -> stop.getLocationType() != StudentStopLocationType.SCHOOL)
                .toList();
        LocalDate today = LocalDate.now(TripService.ZONE);
        for (StudentStop stop : stops) {
            if (tripRepository.findFirstByRouteIdAndDateAndStatusIn(stop.getStop().getRoute().getId(), today, ACTIVE_TRIP).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Tem uma viagem rodando com esse endereço agora. Tente depois que ela terminar");
            }
        }
        studentAddressRepository.delete(studentAddress);
        Map<Route, List<StudentStop>> byRoute = new LinkedHashMap<>();
        stops.forEach(stop -> byRoute.computeIfAbsent(stop.getStop().getRoute(), k -> new ArrayList<>()).add(stop));
        studentStopRepository.deleteAll(stops);
        String studentName = studentAddress.getStudent().getName();
        byRoute.forEach((route, removed) -> notificationService.send(
                routeDriverRepository.findByRouteIdAndEndDateIsNull(route.getId()).stream().map(rd -> rd.getDriver().getUserId()).toList(),
                NotificationType.ROUTE, "Endereço removido",
                studentName + " não usa mais o endereço \"" + studentAddress.getLabel() + "\". Ele saiu do ponto "
                        + removed.get(0).getStop().getOrderIndex() + " da rota " + route.getName() + ": escolha o novo ponto",
                Map.of("routeId", route.getId(), "studentId", studentId)));
    }

    private void checkResponsible(UUID studentId, UUID responsibleId) {
        if (!studentResponsibleRepository.existsByResponsible_UserIdAndStudent_Id(responsibleId, studentId)) {
            throw new AccessDeniedException("Responsável não vinculado a esse aluno");
        }
    }

    public static StudentPlaceResponseDTO toResponse(StudentAddress studentAddress) {
        Address a = studentAddress.getAddress();
        return new StudentPlaceResponseDTO(studentAddress.getId(), studentAddress.getLabel(), new AddressResponseDTO(
                a.getId(), a.getStreet(), a.getZipCode(), a.getCity(), a.getNeighborhood(), a.getNumber(), a.getState(),
                a.getLatitude(), a.getLongitude()));
    }
}

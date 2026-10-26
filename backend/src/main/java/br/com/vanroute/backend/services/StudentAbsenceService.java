package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import br.com.vanroute.backend.dtos.trip.AbsenceRequestDTO;
import br.com.vanroute.backend.dtos.trip.AbsenceResponseDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.chat.Chat;
import br.com.vanroute.backend.models.chat.enums.MessageType;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteDriver;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.trip.StudentAbsence;
import br.com.vanroute.backend.repositories.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

// o responsavel avisa que o filho nao vai numa rota num dia: sai da trip na hora e o motorista e avisado no chat
@Service
public class StudentAbsenceService {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StudentAbsenceRepository studentAbsenceRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;
    private final StudentRepository studentRepository;
    private final StudentStopRepository studentStopRepository;
    private final RouteRepository routeRepository;
    private final RouteDriverRepository routeDriverRepository;
    private final UserRepository userRepository;
    private final TripService tripService;
    private final ChatService chatService;
    private final ChatMessageService chatMessageService;
    private final NotificationService notificationService;

    public StudentAbsenceService(StudentAbsenceRepository studentAbsenceRepository, StudentResponsibleRepository studentResponsibleRepository,
                                 StudentRepository studentRepository, StudentStopRepository studentStopRepository,
                                 RouteRepository routeRepository, RouteDriverRepository routeDriverRepository,
                                 UserRepository userRepository, TripService tripService,
                                 ChatService chatService, ChatMessageService chatMessageService,
                                 NotificationService notificationService) {
        this.studentAbsenceRepository = studentAbsenceRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.studentRepository = studentRepository;
        this.studentStopRepository = studentStopRepository;
        this.routeRepository = routeRepository;
        this.routeDriverRepository = routeDriverRepository;
        this.userRepository = userRepository;
        this.tripService = tripService;
        this.chatService = chatService;
        this.chatMessageService = chatMessageService;
        this.notificationService = notificationService;
    }

    @Transactional
    public AbsenceResponseDTO create(UUID studentId, AbsenceRequestDTO request, UUID responsibleId) {
        checkResponsible(studentId, responsibleId);
        if (request.date().isBefore(LocalDate.now(TripService.ZONE))) {
            throw new BadRequestException("Não dá para avisar falta em data que já passou");
        }
        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new EntityNotFoundException("Route not found"));
        boolean inRoute = studentStopRepository.findRoutesOfStudent(studentId).stream()
                .anyMatch(r -> r.getId().equals(route.getId()));
        if (!inRoute) {
            throw new BadRequestException("O aluno não está nessa rota");
        }
        if (studentAbsenceRepository.existsByStudentIdAndRouteIdAndDate(studentId, route.getId(), request.date())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Falta já avisada para esse dia nessa rota");
        }
        // com a viagem rodando: so enquanto ele ainda nao embarcou
        tripService.checkAbsenceAllowed(route.getId(), studentId, request.date());
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        StudentAbsence absence = new StudentAbsence();
        absence.setStudent(student);
        absence.setRoute(route);
        absence.setDate(request.date());
        absence.setCreatedBy(userRepository.getReferenceById(responsibleId));
        absence = studentAbsenceRepository.save(absence);

        tripService.applyAbsence(absence);
        notifyDrivers(absence, responsibleId, false);
        return toResponse(absence);
    }

    public List<AbsenceResponseDTO> upcoming(UUID studentId, UUID responsibleId) {
        checkResponsible(studentId, responsibleId);
        return studentAbsenceRepository.findByStudentIdAndDateGreaterThanEqualOrderByDate(studentId, LocalDate.now(TripService.ZONE))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // "ah, ele vai sim": so antes da viagem do dia comecar; o motorista e avisado
    @Transactional
    public void cancel(UUID studentId, UUID absenceId, UUID responsibleId) {
        checkResponsible(studentId, responsibleId);
        StudentAbsence absence = studentAbsenceRepository.findById(absenceId)
                .filter(a -> a.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new EntityNotFoundException("Absence not found"));
        if (absence.getDate().isBefore(LocalDate.now(TripService.ZONE))) {
            throw new BadRequestException("Não dá para desfazer falta de data que já passou");
        }
        tripService.checkAbsenceUndoAllowed(absence.getRoute().getId(), absence.getDate());
        notifyDrivers(absence, responsibleId, true);
        studentAbsenceRepository.delete(absence);
    }

    private void checkResponsible(UUID studentId, UUID responsibleId) {
        if (!studentResponsibleRepository.existsByResponsible_UserIdAndStudent_Id(responsibleId, studentId)) {
            throw new AccessDeniedException("Responsável não vinculado a esse aluno");
        }
    }

    // mensagem automatica no chat entre o responsavel e cada motorista da rota
    private void notifyDrivers(StudentAbsence absence, UUID responsibleId, boolean canceled) {
        String date = absence.getDate().format(BR_DATE);
        String text = canceled
                ? absence.getStudent().getName() + " vai sim na rota " + absence.getRoute().getName() + " em " + date
                : absence.getStudent().getName() + " não vai na rota " + absence.getRoute().getName() + " em " + date;
        String payload = "{\"studentId\": \"" + absence.getStudent().getId() + "\", \"routeId\": \"" + absence.getRoute().getId()
                + "\", \"date\": \"" + absence.getDate() + "\", \"canceled\": " + canceled + "}";
        for (RouteDriver routeDriver : routeDriverRepository.findByRouteIdAndEndDateIsNull(absence.getRoute().getId())) {
            Chat chat = chatService.createOrGetChat(routeDriver.getDriver().getUserId(), responsibleId);
            ChatMessageResponseDTO message = chatMessageService.sendSystemMessage(chat.getId(), responsibleId, text, MessageType.ABSENCE, payload);
            chatMessageService.publish(chat.getId(), message);
            notificationService.send(List.of(routeDriver.getDriver().getUserId()), NotificationType.ABSENCE_REPORTED,
                    canceled ? "Falta desfeita" : "Falta avisada", text,
                    Map.of("chatId", chat.getId(), "studentId", absence.getStudent().getId(), "routeId", absence.getRoute().getId(),
                            "date", absence.getDate().toString(), "canceled", canceled));
        }
    }

    private AbsenceResponseDTO toResponse(StudentAbsence absence) {
        return new AbsenceResponseDTO(absence.getId(), absence.getStudent().getId(), absence.getRoute().getId(),
                absence.getRoute().getName(), absence.getDate());
    }
}

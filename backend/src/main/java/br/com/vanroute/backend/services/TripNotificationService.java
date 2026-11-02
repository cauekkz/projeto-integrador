package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.trip.TripLiveStateDTO;
import br.com.vanroute.backend.dtos.trip.TripLiveStopDTO;
import br.com.vanroute.backend.dtos.trip.TripStudentDTO;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import br.com.vanroute.backend.models.trip.Boarding;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.repositories.BoardingRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// notificacoes da viagem pros responsaveis (todos os responsaveis do aluno, nao so o admin).
// irmaos na mesma viagem viram uma notificacao so ("Ana e Joao embarcaram")
@Service
public class TripNotificationService {

    private static final Set<TripStatus> ACTIVE = EnumSet.of(TripStatus.STARTED, TripStatus.IN_PROGRESS, TripStatus.PAUSED);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationService notificationService;
    private final BoardingRepository boardingRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;

    public TripNotificationService(NotificationService notificationService, BoardingRepository boardingRepository,
                                   StudentResponsibleRepository studentResponsibleRepository) {
        this.notificationService = notificationService;
        this.boardingRepository = boardingRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
    }

    // "a van saiu": pra quem tem filho que vai hoje (ausente avisado nao recebe)
    public void tripStarted(Trip trip, boolean restart) {
        Map<UUID, String> going = boardingRepository.findByTripWithStudent(trip.getId()).stream()
                .filter(boarding -> boarding.getStatus() == BoardingStatus.PENDING)
                .map(Boarding::getStudent)
                .collect(Collectors.toMap(Student::getId, Student::getName, (a, b) -> a, LinkedHashMap::new));
        String driver = trip.getDriver().getUser().getName();
        byParent(going).forEach((parentId, kids) -> notificationService.send(List.of(parentId), NotificationType.TRIP_STARTED,
                restart ? "A rota foi reiniciada" : "A van saiu",
                driver + (restart ? " reiniciou a rota " : " iniciou a rota ") + trip.getRoute().getName() + " (" + names(kids, going) + ")",
                Map.of("tripId", trip.getId(), "routeId", trip.getRoute().getId(), "studentIds", kids)));
    }

    // o proximo ponto da van mudou: avisa quem tem filho nesse ponto (buscar em casa, levar pra escola ou pra casa)
    public void nextStopChanged(Trip trip, TripLiveStateDTO before, TripLiveStateDTO after) {
        if (!ACTIVE.contains(trip.getStatus()) || after == null) {
            return;
        }
        TripLiveStopDTO next = nextStop(after);
        TripLiveStopDTO previous = before == null ? null : nextStop(before);
        if (next == null || (previous != null && previous.routeStopId().equals(next.routeStopId()))) {
            return;
        }
        Map<UUID, TripStudentDTO> waiting = next.students().stream()
                .filter(TripLiveService::pendingAtStop)
                .collect(Collectors.toMap(TripStudentDTO::studentId, s -> s, (a, b) -> a, LinkedHashMap::new));
        Map<UUID, String> names = waiting.values().stream()
                .collect(Collectors.toMap(TripStudentDTO::studentId, TripStudentDTO::studentName, (a, b) -> a, LinkedHashMap::new));
        // tempo do ultimo calculo menos o que ja passou desde ele (igual ao que o WebSocket mostra)
        Double remaining = next.etaSeconds() == null ? null : after.computedAt() == null ? next.etaSeconds()
                : Math.max(0, next.etaSeconds() - (System.currentTimeMillis() - after.computedAt()) / 1000.0);
        String eta = remaining == null ? "" : " Chega em cerca de " + Math.max(1, Math.round(remaining / 60)) + " min.";
        byParent(names).forEach((parentId, kids) -> {
            TripStudentDTO first = waiting.get(kids.get(0));
            String who = names(kids, names);
            String title;
            if (first.action() == StudentStopAction.BOARDING && first.locationType() == StudentStopLocationType.SCHOOL) {
                title = "A van está indo buscar " + who + " na escola";
            } else if (first.action() == StudentStopAction.BOARDING) {
                title = "A van está indo buscar " + who;
            } else if (first.locationType() == StudentStopLocationType.SCHOOL) {
                title = who + (kids.size() > 1 ? " estão" : " está") + " a caminho da escola";
            } else {
                title = who + (kids.size() > 1 ? " estão" : " está") + " a caminho de casa";
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tripId", trip.getId());
            payload.put("routeStopId", next.routeStopId());
            payload.put("studentIds", kids);
            if (remaining != null) {
                payload.put("etaSeconds", Math.round(remaining));
            }
            notificationService.send(List.of(parentId), NotificationType.VAN_ON_THE_WAY, title,
                    "Rota " + trip.getRoute().getName() + "." + eta, payload);
        });
    }

    public void boarded(Trip trip, Student student) {
        toParents(student, NotificationType.STUDENT_BOARDED, student.getName() + " embarcou",
                "Embarcou na van às " + LocalTime.now(TripService.ZONE).format(HOUR) + " (rota " + trip.getRoute().getName() + ")",
                trip);
    }

    // onde desceu: escola ou casa (vem do ponto de desembarque do aluno no estado da trip)
    public void delivered(Trip trip, Student student, TripLiveStateDTO state) {
        boolean school = state != null && state.stops() != null && state.stops().stream()
                .flatMap(stop -> stop.students().stream())
                .anyMatch(s -> s.studentId().equals(student.getId()) && s.action() == StudentStopAction.ALIGHTING
                        && s.locationType() == StudentStopLocationType.SCHOOL);
        toParents(student, NotificationType.STUDENT_DELIVERED,
                student.getName() + (school ? " chegou na escola" : " chegou em casa"),
                "Desceu da van às " + LocalTime.now(TripService.ZONE).format(HOUR) + " (rota " + trip.getRoute().getName() + ")",
                trip);
    }

    // motorista pulou o aluno (nao apareceu, ninguem respondeu)
    public void absentByDriver(Trip trip, Student student) {
        toParents(student, NotificationType.STUDENT_ABSENT, student.getName() + " não embarcou",
                "O motorista marcou " + student.getName() + " como ausente hoje na rota " + trip.getRoute().getName(),
                trip);
    }

    private void toParents(Student student, NotificationType type, String title, String message, Trip trip) {
        notificationService.send(studentResponsibleRepository.findResponsibleUserIds(student.getId()), type, title, message,
                Map.of("tripId", trip.getId(), "studentIds", List.of(student.getId())));
    }

    // responsavel -> filhos dele (na ordem), pra mandar uma notificacao so por responsavel
    private Map<UUID, List<UUID>> byParent(Map<UUID, String> students) {
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        students.keySet().forEach(studentId -> studentResponsibleRepository.findResponsibleUserIds(studentId)
                .forEach(parentId -> result.computeIfAbsent(parentId, k -> new ArrayList<>()).add(studentId)));
        return result;
    }

    private static String names(List<UUID> ids, Map<UUID, String> names) {
        List<String> list = ids.stream().map(names::get).toList();
        return list.size() == 1 ? list.get(0)
                : String.join(", ", list.subList(0, list.size() - 1)) + " e " + list.get(list.size() - 1);
    }

    static TripLiveStopDTO nextStop(TripLiveStateDTO state) {
        return state.stops() == null ? null : state.stops().stream().filter(stop -> !stop.done()).findFirst().orElse(null);
    }
}

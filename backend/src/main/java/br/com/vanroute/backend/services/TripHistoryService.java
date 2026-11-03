package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.trip.TripHistoryItemDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// historico das viagens por aluno: o responsavel ve as dos filhos, o motorista as que ele dirigiu
@Service
public class TripHistoryService {

    private static final String FROM = "FROM Boarding b JOIN b.trip t JOIN t.route rt JOIN b.student s "
            + "LEFT JOIN t.driver d LEFT JOIN d.user du "
            + "WHERE (d.userId = :me OR EXISTS (SELECT 1 FROM StudentResponsible sr WHERE sr.student = s AND sr.responsible.userId = :me))";

    private final EntityManager entityManager;

    public TripHistoryService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record Filter(UUID studentId, UUID driverId, UUID routeId, LocalDate from, LocalDate to) {
    }

    // mais recente primeiro; todos os filtros sao opcionais
    @Transactional(readOnly = true)
    public Page<TripHistoryItemDTO> list(UUID userId, Filter filter, Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
            throw new BadRequestException("O fim do período não pode ser antes do início");
        }
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        params.put("me", userId);
        condition(where, params, "s.id = :studentId", "studentId", filter.studentId());
        condition(where, params, "d.userId = :driverId", "driverId", filter.driverId());
        condition(where, params, "rt.id = :routeId", "routeId", filter.routeId());
        condition(where, params, "t.date >= :from", "from", filter.from());
        condition(where, params, "t.date <= :to", "to", filter.to());

        TypedQuery<TripHistoryItemDTO> query = entityManager.createQuery(
                "SELECT new br.com.vanroute.backend.dtos.trip.TripHistoryItemDTO(t.id, t.date, t.status, t.startTime, t.endTime, "
                        + "rt.id, rt.name, d.userId, du.name, s.id, s.name, b.status, b.boardingTime, b.unboardingTime, 0L) "
                        + FROM + where + " ORDER BY t.date DESC, t.startTime DESC, s.name", TripHistoryItemDTO.class);
        TypedQuery<Long> count = entityManager.createQuery("SELECT COUNT(b) " + FROM + where, Long.class);
        params.forEach((name, value) -> {
            query.setParameter(name, value);
            count.setParameter(name, value);
        });
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<TripHistoryItemDTO> rows = query.getResultList();

        Map<UUID, Long> occurrences = new HashMap<>();
        List<UUID> tripIds = rows.stream().map(TripHistoryItemDTO::tripId).distinct().toList();
        if (!tripIds.isEmpty()) {
            entityManager.createQuery("SELECT o.trip.id, COUNT(o) FROM Occurrence o WHERE o.trip.id IN :tripIds GROUP BY o.trip.id",
                            Object[].class)
                    .setParameter("tripIds", tripIds)
                    .getResultList()
                    .forEach(row -> occurrences.put((UUID) row[0], (Long) row[1]));
        }
        List<TripHistoryItemDTO> content = rows.stream()
                .map(row -> row.withOccurrences(occurrences.getOrDefault(row.tripId(), 0L)))
                .toList();
        return new PageImpl<>(content, pageable, count.getSingleResult());
    }

    private static void condition(StringBuilder where, Map<String, Object> params, String clause, String name, Object value) {
        if (value != null) {
            where.append(" AND ").append(clause);
            params.put(name, value);
        }
    }
}

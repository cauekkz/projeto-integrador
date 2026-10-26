package br.com.vanroute.backend.dtos.route;

import br.com.vanroute.backend.models.shared.enums.WeekDay;

import java.util.Set;
import java.util.UUID;

// dias em que o aluno tem embarque mas nao desembarque na rota (ou o contrario)
public record RoutePendingResponse(
        UUID studentId,
        String studentName,
        Set<WeekDay> missingBoarding,
        Set<WeekDay> missingAlighting
) {
}

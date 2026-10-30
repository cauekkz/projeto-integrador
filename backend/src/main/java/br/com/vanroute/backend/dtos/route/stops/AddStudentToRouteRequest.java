package br.com.vanroute.backend.dtos.route.stops;

import br.com.vanroute.backend.models.shared.enums.WeekDay;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Set;
import java.util.UUID;

// "adicionar aluno na rota" num passo so. Onde:
//   locationType HOME/OTHER -> studentAddressId (um endereco do aluno: "Casa", "Natacao"...)
//   locationType SCHOOL     -> nada (o ponto e o da escola do contrato)
// weekdays: os dias em que ele usa esse ponto (sem dias = todos)
// orderIndex: posicao do ponto, se ele for criado agora (sem = automatico; ponto que ja existe nao se mexe)
public record AddStudentToRouteRequest(
        @NotNull(message = "A ação é obrigatória")
        StudentStopAction action,
        @NotNull(message = "O tipo de local é obrigatório")
        StudentStopLocationType locationType,
        UUID studentAddressId,
        Set<WeekDay> weekdays,
        @Positive(message = "A posição começa em 1")
        Integer orderIndex
) {
}

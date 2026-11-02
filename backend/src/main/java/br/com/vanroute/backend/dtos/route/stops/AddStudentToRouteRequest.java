package br.com.vanroute.backend.dtos.route.stops;

import br.com.vanroute.backend.models.shared.enums.WeekDay;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Set;
import java.util.UUID;

// "adicionar aluno na rota" num passo so. Em qual ponto (obrigatorio, um dos dois):
//   routeStopId   -> um ponto que ja existe na rota (com o endereco certo pro aluno)
//   newStop: true -> cria um ponto novo (pode haver mais de um ponto no mesmo endereco)
// Onde:
//   locationType HOME/OTHER -> studentAddressId (um endereco do aluno: "Casa", "Natacao"...); obrigatorio no ponto novo
//   locationType SCHOOL     -> nada (o ponto e o da escola do contrato)
// weekdays: os dias em que ele usa esse ponto (sem dias = todos)
// orderIndex: so no ponto novo, a posicao dele (sem = automatico)
public record AddStudentToRouteRequest(
        @NotNull(message = "A ação é obrigatória")
        StudentStopAction action,
        @NotNull(message = "O tipo de local é obrigatório")
        StudentStopLocationType locationType,
        UUID studentAddressId,
        UUID routeStopId,
        Boolean newStop,
        Set<WeekDay> weekdays,
        @Positive(message = "A posição começa em 1")
        Integer orderIndex
) {
}

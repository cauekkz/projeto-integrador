package br.com.vanroute.backend.dtos.route.stops;

import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;

import jakarta.validation.constraints.NotNull;

import br.com.vanroute.backend.models.shared.enums.WeekDay;

import java.util.Set;
import java.util.UUID;

public record StudentStopRequestDTO(UUID studentId,
                                    UUID stopId,
                                    @NotNull(message = "O tipo de local é obrigatório")
                                    StudentStopLocationType locationType,
                                    @NotNull(message = "A ação é obrigatória")
                                    StudentStopAction action,
                                    // opcional: sem dias = todos os dias
                                    Set<WeekDay> weekdays) {

}

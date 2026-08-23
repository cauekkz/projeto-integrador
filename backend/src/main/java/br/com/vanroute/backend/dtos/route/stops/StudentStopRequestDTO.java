package br.com.vanroute.backend.dtos.route.stops;

import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;

import java.util.UUID;

public record StudentStopRequestDTO(UUID studentId,
                                    UUID stopId,
                                    StudentStopLocationType locationType,
                                    StudentStopAction action) {

}

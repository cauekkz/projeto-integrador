package br.com.vanroute.backend.dtos.student;

import br.com.vanroute.backend.dtos.user.ResponsibleResponseDTO;

public record StudentResponsibleResponseDTO(
        StudentResponseDTO studentResponseDTO,
        ResponsibleResponseDTO responsibleResponseDTO
) {
}

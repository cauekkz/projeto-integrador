package br.com.vanroute.backend.dtos.route;

import br.com.vanroute.backend.dtos.route.stops.SchoolStopResponseDTO;
import br.com.vanroute.backend.dtos.route.stops.StudentStopResponseDTO;

import java.util.List;
import java.util.UUID;

public record RouteStopResponse(
        UUID id,
        Integer orderIndex,
        AddressResponseDTO address,
        // alunos e escolas ligados ao ponto (com os ids pra mover/excluir)
        List<StudentStopResponseDTO> students,
        List<SchoolStopResponseDTO> schools
) {
}


package br.com.vanroute.backend.dtos.route;

import br.com.vanroute.backend.dtos.student.StudentPlaceResponseDTO;

import java.util.List;
import java.util.UUID;

// quem o motorista pode pôr na rota: os alunos com contrato ativo com ele e os enderecos de cada um
// (a escola nao entra aqui: e sempre a do contrato)
public record RouteStopOptionsDTO(
        List<StudentOption> students
) {
    public record StudentOption(UUID studentId, String studentName, List<StudentPlaceResponseDTO> addresses) {
    }
}

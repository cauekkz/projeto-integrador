package br.com.vanroute.backend.dtos.route;

import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AddRouteStopRequest(
        // endereco onde a van para; alunos e escolas sao ligados ao ponto depois
        @Valid
        @NotNull(message = "O endereço é obrigatório")
        AddressRequestDTO address
) {
}

package br.com.vanroute.backend.dtos.user;

import java.util.List;
import java.util.UUID;

// dados do usuario logado; linkCode so vem preenchido pra motorista (codigo que ele passa pros pais)
public record UserMeResponseDTO(
        UUID id,
        String name,
        String email,
        String phone,
        String cpf,
        List<String> roles,
        String photoKey,
        String linkCode
) {
}

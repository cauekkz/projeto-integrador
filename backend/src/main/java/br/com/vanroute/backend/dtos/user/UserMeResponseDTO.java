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
        String linkCode,
        // versao atual dos termos e se esse usuario ja aceitou; false = o app mostra os termos e chama POST /api/terms/accept
        String termsVersion,
        boolean termsAccepted
) {
}

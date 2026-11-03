package br.com.vanroute.backend.dtos.group;

import jakarta.validation.constraints.Size;

// motivo opcional; a mensagem padrao sempre vai ("nao vai buscar hoje")
public record GroupNoticeRequestDTO(
        @Size(max = 300, message = "O motivo deve ter no máximo 300 caracteres")
        String reason
) {
}

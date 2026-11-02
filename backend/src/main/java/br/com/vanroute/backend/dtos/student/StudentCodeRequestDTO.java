package br.com.vanroute.backend.dtos.student;
import jakarta.validation.constraints.NotBlank;

public record StudentCodeRequestDTO(
        @NotBlank(message = "O código é obrigatório")
        String code
) {}
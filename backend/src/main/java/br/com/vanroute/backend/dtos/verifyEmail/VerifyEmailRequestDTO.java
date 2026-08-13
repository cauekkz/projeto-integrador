package br.com.vanroute.backend.dtos.verifyEmail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequestDTO (
    @Email(message = "E-mail inválido")
    @NotBlank(message = "O e-mail é obrigatório")
    String email,

    @Size(min = 8, message = "O código possui 6 digitos")
    String code


)
{}
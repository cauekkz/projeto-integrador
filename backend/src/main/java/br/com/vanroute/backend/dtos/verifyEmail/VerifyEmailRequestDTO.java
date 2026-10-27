package br.com.vanroute.backend.dtos.verifyEmail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequestDTO (
    @Email(message = "E-mail inválido")
    @NotBlank(message = "O e-mail é obrigatório")
    String email,

    @NotBlank(message = "O código é obrigatório")
    @Pattern(regexp = "\\d{6}", message = "O código possui 6 dígitos")
    String code


)
{}
package br.com.vanroute.backend.dtos.inviteCode;

import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InviteCodeRequestDTO(
        @NotBlank(message = "Código de convite não pode ser vazio")
        @Length(min = 9, max = 9, message = "Código de convite deve ter 9 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9]{9}$")
        String inviteCode) {}
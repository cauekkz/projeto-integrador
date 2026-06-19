package br.com.vanroute.backend.dtos.user;

import jakarta.validation.constraints.NotBlank;

public class LoginRequestDTO {
    @NotBlank
    private String cpf;
    @NotBlank
    private String passwordHash;

    public LoginRequestDTO() {
    }

    public LoginRequestDTO(String cpf, String passwordHash) {
        this.cpf = cpf;
        this.passwordHash = passwordHash;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}

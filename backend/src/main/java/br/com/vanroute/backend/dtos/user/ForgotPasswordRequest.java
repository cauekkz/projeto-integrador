package br.com.vanroute.backend.dtos.user;

import jakarta.validation.constraints.NotNull;

public class ForgotPasswordRequest {

    @NotNull(message = "Digite a nova senha")
    private String newPassword;
    @NotNull(message = "Codigo inválido")
    private String code;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

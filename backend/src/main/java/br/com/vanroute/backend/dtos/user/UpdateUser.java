package br.com.vanroute.backend.dtos.user;

import jakarta.validation.constraints.NotBlank;

public class UpdateUser {
    @NotBlank(message = "A senha é obrigatória")
    private String email;
    @NotBlank(message = "O telefone é obrigatório")
    private String phone;
    @NotBlank(message = "O codigo é obrigatório")
    private String code;


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

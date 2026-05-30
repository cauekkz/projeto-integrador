package br.com.vanroute.backend.dtos.user;

import jakarta.validation.constraints.NotBlank;

public class UserCreateDTO {
    @NotBlank(message = "O nome é obrigatório")
    private String name;
    @NotBlank(message = "O cpf é obrigatório")
    private String cpf;
    @NotBlank(message = "A senha é obrigatória")
    private String password;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getCpf() {
        return cpf;
    }
    public void setCpf(String cpf) {
        this.cpf = cpf;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }   
    
}

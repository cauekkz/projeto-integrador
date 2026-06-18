package br.com.vanroute.backend.dtos.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;

public class UserCreateDTO {
    @NotBlank(message = "O nome é obrigatório")
    private String name;
    @NotBlank(message = "O cpf é obrigatório")
    private String cpf;
    @NotBlank(message = "A senha é obrigatória")
    private String passwordHash;
    @NotNull(message = "O tipo de usuário (role) é obrigatório")
    private RoleTypeEnum role;

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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public RoleTypeEnum getRole() {
        return role;
    }

    public void setRole(RoleTypeEnum role) {
        this.role = role;
    }
}

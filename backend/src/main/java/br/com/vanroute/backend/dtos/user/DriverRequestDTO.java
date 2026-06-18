package br.com.vanroute.backend.dtos.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;
import br.com.vanroute.backend.validators.password.PasswordMatch;
@PasswordMatch
public class DriverRequestDTO {
    @NotBlank(message = "O tipo do motorista é obrigatório")
    private String driverType;

    @NotNull(message = "O documento PDF é obrigatório")
    private MultipartFile documentPdf;


    @NotBlank(message = "O nome é obrigatório")
    private String name;

    @Email(message = "E-mail inválido")
    @NotBlank(message = "O e-mail é obrigatório")
    private String email;

    @NotBlank(message = "A senha é obrigatória")
    private String password;

    @NotBlank(message = "A confirmação de senha é obrigatória")
    private String confirmPassword;

    @NotBlank(message = "O telefone é obrigatório")
    private String phone;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getConfirmPassword() {
        return confirmPassword;
    }
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }   


    public String getDriverType() {
        return driverType;
    }

    public void setDriverType(String driverType) {
        this.driverType = driverType;
    }

    public MultipartFile getDocumentPdf() {
        return documentPdf;
    }

    public void setDocumentPdf(MultipartFile documentPdf) {
        this.documentPdf = documentPdf;
    }



}

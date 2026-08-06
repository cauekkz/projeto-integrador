package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.user.ResponsibleRequestDTO;
import br.com.vanroute.backend.dtos.user.ResponsibleResponseDTO;
import br.com.vanroute.backend.services.ResponsibleService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import br.com.vanroute.backend.services.EmailVerificationService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/responsible")
public class ResponsibleController {

    private final ResponsibleService responsibleService;
    private final EmailVerificationService emailVerificationService;

    public ResponsibleController(ResponsibleService responsibleService, EmailVerificationService emailVerificationService) {
        this.responsibleService = responsibleService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/signup")
    public ResponsibleResponseDTO signUp(@RequestBody ResponsibleRequestDTO responsibleRequestDTO){
        ResponsibleResponseDTO response = responsibleService.createResponsible(responsibleRequestDTO);
        emailVerificationService.generateAndSendCode("verificationEmail:email:" + response.email(), response.email());
        return response;
    }
}

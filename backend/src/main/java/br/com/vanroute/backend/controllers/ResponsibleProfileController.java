package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.user.ResponsibleProfileResponseDTO;
import br.com.vanroute.backend.services.ResponsibleProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// o motorista ve o perfil do responsavel com quem conversa ou tem contrato
@RestController
@RequestMapping("/api/responsible/{responsibleId}")
public class ResponsibleProfileController {

    private final ResponsibleProfileService responsibleProfileService;

    public ResponsibleProfileController(ResponsibleProfileService responsibleProfileService) {
        this.responsibleProfileService = responsibleProfileService;
    }

    @GetMapping("/profile")
    public ResponsibleProfileResponseDTO profile(@PathVariable UUID responsibleId, Authentication authentication) {
        return responsibleProfileService.profile(responsibleId, AuthenticatedUser.id(authentication));
    }
}

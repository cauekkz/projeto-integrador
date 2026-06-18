package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.user.ResponsibleRequestDTO;
import br.com.vanroute.backend.dtos.user.ResponsibleResponseDTO;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.services.ResponsibleService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/responsible")
public class ResponsibleController {

    private final ResponsibleService responsibleService;

    public ResponsibleController(ResponsibleService responsibleService) {
        this.responsibleService = responsibleService;
    }

    @PostMapping("/signup")
    public ResponsibleResponseDTO signUp(@RequestBody ResponsibleRequestDTO responsibleRequestDTO){
        return responsibleService.createResponsible(responsibleRequestDTO);
    }
}

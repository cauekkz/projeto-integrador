package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.school.SchoolRequestDTO;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.services.DriverSchoolService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/school")
public class SchoolController {

    private final DriverSchoolService schoolService;

    public SchoolController(DriverSchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @PostMapping("/add-school")
    public School createSchool(@RequestBody SchoolRequestDTO dto, Authentication auth){
        String userCpf = auth.getName();
        return schoolService.createSchool(dto, userCpf);
    }
}

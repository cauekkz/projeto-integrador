package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.school.SchoolRequestDTO;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.services.SchoolService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/school")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @PostMapping("/add-school")
    public School createSchool(@RequestBody SchoolRequestDTO dto){
        return schoolService.createSchool(dto);
    }
}

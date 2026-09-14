package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.school.SchoolStop;
import br.com.vanroute.backend.repositories.RouteStopRepository;
import br.com.vanroute.backend.repositories.SchoolRepository;
import br.com.vanroute.backend.repositories.SchoolStopRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SchoolStopService {

    private final SchoolStopRepository schoolStopRepository;
    private final SchoolRepository schoolRepository;
    private final RouteStopRepository routeStopRepository;


    public SchoolStopService(SchoolStopRepository schoolStopRepository, SchoolRepository schoolRepository, RouteStopRepository routeStopRepository) {
        this.schoolStopRepository = schoolStopRepository;
        this.schoolRepository = schoolRepository;
        this.routeStopRepository = routeStopRepository;
    }

    public void addSchoolStop(UUID schoolId, UUID routeStopId){
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new RuntimeException("School not found."));
        RouteStop route = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new RuntimeException("School not found"));
        SchoolStop schoolStop = new SchoolStop();
        schoolStop.setSchool(school);
        schoolStop.setStop(route);
        schoolStopRepository.save(schoolStop);
    }

}

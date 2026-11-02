package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.school.SchoolStop;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.SchoolStopRepository;
import br.com.vanroute.backend.repositories.StudentStopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchoolStopServiceTest {

    @Mock private SchoolStopRepository schoolStopRepository;
    @Mock private RouteService routeService;
    @Mock private StudentStopRepository studentStopRepository;
    @Mock private Authentication authentication;

    @InjectMocks
    private SchoolStopService schoolStopService;

    private RouteStop stop;
    private School school;
    private Driver driver;

    @BeforeEach
    void setUp() {
        Route route = new Route();
        route.setId(UUID.randomUUID());
        stop = new RouteStop();
        stop.setId(UUID.randomUUID());
        stop.setRoute(route);
        school = new School();
        school.setId(UUID.randomUUID());
        driver = new Driver();
        driver.setUserId(UUID.randomUUID());

        when(routeService.checkOwnership(route, authentication)).thenReturn(driver);
    }

    private SchoolStop existingSchoolStop() {
        SchoolStop schoolStop = new SchoolStop();
        schoolStop.setId(UUID.randomUUID());
        schoolStop.setSchool(school);
        schoolStop.setStop(stop);
        when(schoolStopRepository.findById(schoolStop.getId())).thenReturn(Optional.of(schoolStop));
        return schoolStop;
    }

    @Test
    void shouldNotDeleteSchoolUsedByStudentsOfTheRoute() {
        SchoolStop schoolStop = existingSchoolStop();
        when(studentStopRepository.existsSchoolStudentAtStop(stop.getId(), school.getId())).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> schoolStopService.deleteSchoolStop(schoolStop.getId(), authentication));
        verify(schoolStopRepository, never()).delete(any());
    }

    @Test
    void shouldDeleteUnusedSchoolStop() {
        SchoolStop schoolStop = existingSchoolStop();

        schoolStopService.deleteSchoolStop(schoolStop.getId(), authentication);

        verify(schoolStopRepository).delete(schoolStop);
    }
}

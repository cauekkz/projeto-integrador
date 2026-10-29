package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.school.SchoolStop;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.DriverSchoolsRepository;
import br.com.vanroute.backend.repositories.RouteStopRepository;
import br.com.vanroute.backend.repositories.SchoolRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchoolStopServiceTest {

    @Mock private SchoolStopRepository schoolStopRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private RouteStopRepository routeStopRepository;
    @Mock private DriverSchoolsRepository driverSchoolsRepository;
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

        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(routeStopRepository.findById(stop.getId())).thenReturn(Optional.of(stop));
        when(routeService.checkOwnership(route, authentication)).thenReturn(driver);
    }

    @Test
    void shouldLinkSchoolToStop() {
        when(driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driver.getUserId(), school.getId())).thenReturn(true);
        when(schoolStopRepository.findFirstByStopRouteIdAndSchoolId(stop.getRoute().getId(), school.getId())).thenReturn(Optional.empty());
        when(schoolStopRepository.save(any(SchoolStop.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(stop.getId(), schoolStopService.addSchoolStop(school.getId(), stop.getId(), authentication).stopId());
    }

    @Test
    void shouldRejectSchoolNotLinkedToDriver() {
        when(driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driver.getUserId(), school.getId())).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> schoolStopService.addSchoolStop(school.getId(), stop.getId(), authentication));
        verify(schoolStopRepository, never()).save(any());
    }

    @Test
    void shouldRejectSchoolAlreadyInRoute() {
        when(driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driver.getUserId(), school.getId())).thenReturn(true);
        when(schoolStopRepository.findFirstByStopRouteIdAndSchoolId(stop.getRoute().getId(), school.getId())).thenReturn(Optional.of(new SchoolStop()));

        assertThrows(ResponseStatusException.class, () -> schoolStopService.addSchoolStop(school.getId(), stop.getId(), authentication));
    }

    @Test
    void shouldRejectStopWithHomeOrOtherStudents() {
        when(driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driver.getUserId(), school.getId())).thenReturn(true);
        when(schoolStopRepository.findFirstByStopRouteIdAndSchoolId(stop.getRoute().getId(), school.getId())).thenReturn(Optional.empty());
        when(studentStopRepository.existsByStopIdAndLocationTypeIn(eq(stop.getId()), anyCollection())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> schoolStopService.addSchoolStop(school.getId(), stop.getId(), authentication));
        verify(schoolStopRepository, never()).save(any());
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
        when(studentStopRepository.existsStudentUsingSchoolInRoute(stop.getRoute().getId(), school.getId())).thenReturn(true);

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

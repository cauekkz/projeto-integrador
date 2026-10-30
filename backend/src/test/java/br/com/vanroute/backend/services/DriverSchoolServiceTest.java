package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.school.SchoolRequestDTO;
import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.models.school.DriverSchools;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.DriverSchoolsRepository;
import br.com.vanroute.backend.repositories.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverSchoolServiceTest {

    @Mock private SchoolRepository schoolRepository;
    @Mock private AddressService addressService;
    @Mock private MapService mapService;
    @Mock private DriverRepository driverRepository;
    @Mock private DriverSchoolsRepository driverSchoolsRepository;

    @InjectMocks
    private DriverSchoolService driverSchoolService;

    private Driver driver;
    private School existing;
    private SchoolRequestDTO dto;

    @BeforeEach
    void setUp() {
        driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        existing = new School();
        existing.setId(UUID.randomUUID());
        dto = new SchoolRequestDTO("Escola X", "11", "escola@teste.local",
                new AddressRequestDTO("Rua A", "01000-000", "São Paulo", null, 1, "SP", null, null));

        when(driverRepository.findById(driver.getUserId())).thenReturn(Optional.of(driver));
        when(schoolRepository.findByNameAndAddress_Street("Escola X", "Rua A")).thenReturn(Optional.of(existing));
    }

    @Test
    void shouldLinkDriverToExistingSchool() {
        when(driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driver.getUserId(), existing.getId())).thenReturn(false);

        School result = driverSchoolService.createSchool(dto, driver.getUserId());

        assertSame(existing, result);
        ArgumentCaptor<DriverSchools> captor = ArgumentCaptor.forClass(DriverSchools.class);
        verify(driverSchoolsRepository).save(captor.capture());
        assertSame(driver, captor.getValue().getDriver());
        assertSame(existing, captor.getValue().getSchool());
        verify(schoolRepository, never()).save(any());
        verify(addressService, never()).findOrCreate(any());
    }

    @Test
    void shouldRejectWhenDriverAlreadyLinked() {
        when(driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driver.getUserId(), existing.getId())).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> driverSchoolService.createSchool(dto, driver.getUserId()));
        verify(driverSchoolsRepository, never()).save(any());
    }
}

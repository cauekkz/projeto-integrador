package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.vehicle.VehicleResponseDTO;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.vehicle.DriverVehicle;
import br.com.vanroute.backend.models.vehicle.Vehicle;
import br.com.vanroute.backend.models.vehicle.enums.VehicleStatus;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.DriverVehicleRepository;
import br.com.vanroute.backend.repositories.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VehicleServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private DriverVehicleRepository driverVehicleRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private IcpValidationService icpValidationService;
    @Mock private CrlvExtractionService crlvExtractionService;

    @InjectMocks
    private VehicleService vehicleService;

    private final MockMultipartFile crlv = new MockMultipartFile("documentPdf", new byte[]{1});
    private final CrlvExtractionService.CrlvData data = new CrlvExtractionService.CrlvData(
            "ABC1D23", "12345678901", "9BM906133LB000000", "M.BENZ/SPRINTER", 2019, 2020,
            "BRANCA", "DIESEL", "ALUGUEL", 2026, 16);
    private Driver driver;

    @BeforeEach
    void setUp() {
        driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        when(driverRepository.findById(driver.getUserId())).thenReturn(Optional.of(driver));
        when(crlvExtractionService.extract(crlv)).thenReturn(data);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });
    }

    @Test
    void newVehicleIsCreatedFromCrlvAndLinked() {
        when(vehicleRepository.findByPlate("ABC1D23")).thenReturn(Optional.empty());

        VehicleResponseDTO response = vehicleService.addVehicle(crlv, driver.getUserId());

        assertEquals("ABC1D23", response.plate());
        assertEquals(16, response.capacity());
        assertEquals(VehicleStatus.ACTIVE, response.status());
        assertEquals(LocalDate.now(), response.linkedSince());
        verify(driverVehicleRepository).save(any(DriverVehicle.class));
    }

    @Test
    void existingVehicleIsOnlyLinkedWithoutChangingItsData() {
        Vehicle existing = new Vehicle();
        existing.setId(UUID.randomUUID());
        existing.setPlate("ABC1D23");
        existing.setLicensingYear(2024);
        existing.setStatus(VehicleStatus.ACTIVE);
        when(vehicleRepository.findByPlate("ABC1D23")).thenReturn(Optional.of(existing));

        VehicleResponseDTO response = vehicleService.addVehicle(crlv, driver.getUserId());

        assertEquals(existing.getId(), response.id());
        // o CRLV enviado e de 2026, mas a van existente nao e alterada
        assertEquals(2024, existing.getLicensingYear());
        verify(vehicleRepository, never()).save(any());
        ArgumentCaptor<DriverVehicle> link = ArgumentCaptor.forClass(DriverVehicle.class);
        verify(driverVehicleRepository).save(link.capture());
        assertSame(existing, link.getValue().getVehicle());
    }

    @Test
    void sameDriverCannotLinkTheSameVanTwiceWhileActive() {
        Vehicle existing = new Vehicle();
        existing.setId(UUID.randomUUID());
        when(vehicleRepository.findByPlate("ABC1D23")).thenReturn(Optional.of(existing));
        when(driverVehicleRepository.findFirstByDriverUserIdAndVehicleIdAndEndDateIsNull(driver.getUserId(), existing.getId()))
                .thenReturn(Optional.of(new DriverVehicle()));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> vehicleService.addVehicle(crlv, driver.getUserId()));
        assertEquals(409, e.getStatusCode().value());
        verify(driverVehicleRepository, never()).save(any());
    }

    @Test
    void invalidCrlvIsRejected() {
        doThrow(new RuntimeException("assinatura invalida")).when(icpValidationService).validateCrlvSignature(crlv);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> vehicleService.addVehicle(crlv, driver.getUserId()));
        assertEquals(422, e.getStatusCode().value());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void onlyDriversCanAddVehicles() {
        UUID responsibleId = UUID.randomUUID();
        when(driverRepository.findById(responsibleId)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> vehicleService.addVehicle(crlv, responsibleId));
    }

    @Test
    void unlinkSetsEndDateKeepingHistory() {
        UUID vehicleId = UUID.randomUUID();
        DriverVehicle link = new DriverVehicle();
        link.setStartDate(LocalDate.now().minusDays(10));
        when(driverVehicleRepository.findFirstByDriverUserIdAndVehicleIdAndEndDateIsNull(driver.getUserId(), vehicleId))
                .thenReturn(Optional.of(link));

        vehicleService.unlinkVehicle(vehicleId, driver.getUserId());

        assertEquals(LocalDate.now(), link.getEndDate());
        verify(driverVehicleRepository, never()).delete(any());
    }

    @Test
    void vanWithNewPlateIsFoundByRenavamAndOnlyLinked() {
        Vehicle existing = new Vehicle();
        existing.setId(UUID.randomUUID());
        existing.setPlate("ABC1234");
        existing.setRenavam("12345678901");
        when(vehicleRepository.findByRenavam("12345678901")).thenReturn(Optional.of(existing));

        VehicleResponseDTO response = vehicleService.addVehicle(crlv, driver.getUserId());

        assertEquals(existing.getId(), response.id());
        verify(vehicleRepository, never()).findByPlate(any());
        verify(vehicleRepository, never()).save(any());
    }
}

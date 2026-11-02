package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.rating.RatingRequestDTO;
import br.com.vanroute.backend.models.rating.Rating;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DriverProfileServiceTest {

    @Mock private DriverRepository driverRepository;
    @Mock private ResponsibleRepository responsibleRepository;
    @Mock private RatingRepository ratingRepository;
    @Mock private ChatRepository chatRepository;
    @Mock private UserDriverContractRepository userDriverContractRepository;
    @Mock private DriverSchoolsRepository driverSchoolsRepository;
    @Mock private DriverVehicleRepository driverVehicleRepository;

    @InjectMocks
    private DriverProfileService service;

    private final UUID driverId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private Responsible parent;

    @BeforeEach
    void setUp() {
        User driverUser = new User();
        driverUser.setName("Carlos");
        Driver driver = new Driver();
        driver.setUserId(driverId);
        driver.setUser(driverUser);
        User parentUser = new User();
        parentUser.setName("Maria Souza");
        parent = new Responsible();
        parent.setUserId(parentId);
        parent.setUser(parentUser);
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(responsibleRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(ratingRepository.summaryOf(driverId)).thenReturn(Collections.singletonList(new Object[]{4.666, 3L}));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void strangersCannotSeeTheProfile() {
        assertThrows(AccessDeniedException.class, () -> service.profile(driverId, parentId));
    }

    @Test
    void aParentWhoChatsWithTheDriverSeesTheProfileWithTheAverage() {
        when(chatRepository.existsBetween(driverId, parentId)).thenReturn(true);

        var profile = service.profile(driverId, parentId);

        assertEquals("Carlos", profile.name());
        assertEquals(4.7, profile.ratingAverage());
        assertEquals(3, profile.ratingCount());
    }

    @Test
    void onlyWhoHasOrHadAContractRates() {
        when(userDriverContractRepository.existsByDriverUserIdAndResponsibleUserIdAndContractStatusIn(driverId, parentId, DriverProfileService.CAN_RATE))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.rate(driverId, new RatingRequestDTO(5, null), parentId));
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void ratingAgainChangesTheSameRating() {
        when(userDriverContractRepository.existsByDriverUserIdAndResponsibleUserIdAndContractStatusIn(driverId, parentId, DriverProfileService.CAN_RATE))
                .thenReturn(true);
        Rating existing = new Rating();
        existing.setResponsible(parent);
        existing.setScore(2);
        when(ratingRepository.findByResponsibleUserIdAndDriverUserId(parentId, driverId)).thenReturn(Optional.of(existing));

        var response = service.rate(driverId, new RatingRequestDTO(5, " pontual "), parentId);

        assertEquals(5, existing.getScore());
        assertEquals("pontual", response.comment());
        // so o primeiro nome de quem avaliou
        assertEquals("Maria", response.responsibleName());
        verify(ratingRepository).save(existing);
    }

    @Test
    void pendingOrCanceledContractsDoNotCount() {
        assertFalse(DriverProfileService.CAN_RATE.contains(br.com.vanroute.backend.models.contract.enums.ContractStatus.PENDING));
        assertFalse(DriverProfileService.CAN_RATE.contains(br.com.vanroute.backend.models.contract.enums.ContractStatus.CANCELED));
        assertTrue(List.copyOf(DriverProfileService.CAN_RATE).contains(br.com.vanroute.backend.models.contract.enums.ContractStatus.ACTIVE));
    }
}

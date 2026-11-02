package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.rating.RatingRequestDTO;
import br.com.vanroute.backend.dtos.rating.RatingResponseDTO;
import br.com.vanroute.backend.dtos.user.DriverProfileResponseDTO;
import br.com.vanroute.backend.services.DriverProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// perfil do motorista (pro responsavel) e avaliacao
@RestController
@RequestMapping("/api/driver/{driverId}")
public class DriverProfileController {

    private final DriverProfileService driverProfileService;

    public DriverProfileController(DriverProfileService driverProfileService) {
        this.driverProfileService = driverProfileService;
    }

    @GetMapping("/profile")
    public DriverProfileResponseDTO profile(@PathVariable UUID driverId, Authentication authentication) {
        return driverProfileService.profile(driverId, AuthenticatedUser.id(authentication));
    }

    // cria ou troca a nota do responsavel logado
    @PutMapping("/rating")
    public RatingResponseDTO rate(@PathVariable UUID driverId, @RequestBody @Valid RatingRequestDTO request, Authentication authentication) {
        return driverProfileService.rate(driverId, request, AuthenticatedUser.id(authentication));
    }

    @DeleteMapping("/rating")
    public ResponseEntity<Void> deleteRating(@PathVariable UUID driverId, Authentication authentication) {
        driverProfileService.deleteRating(driverId, AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
}

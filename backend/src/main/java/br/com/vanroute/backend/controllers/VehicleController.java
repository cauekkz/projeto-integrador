package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.vehicle.VehicleResponseDTO;
import br.com.vanroute.backend.services.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vehicle")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    //técnica inata
    @PostMapping(value = "/add-vehicle", consumes = {"multipart/form-data"})
    public ResponseEntity<VehicleResponseDTO> addVehicle(@RequestParam("documentPdf") MultipartFile documentPdf,
                                                         Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleService.addVehicle(documentPdf, AuthenticatedUser.id(authentication)));
    }

    @GetMapping("/my-vehicles")
    public List<VehicleResponseDTO> findMyVehicles(Authentication authentication) {
        return vehicleService.findMyVehicles(AuthenticatedUser.id(authentication));
    }

    @PutMapping("/{vehicleId}/unlink")
    public ResponseEntity<Void> unlinkVehicle(@PathVariable UUID vehicleId, Authentication authentication) {
        vehicleService.unlinkVehicle(vehicleId, AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
}

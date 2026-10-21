package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.vehicle.VehicleResponseDTO;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.vehicle.DriverVehicle;
import br.com.vanroute.backend.models.vehicle.Vehicle;
import br.com.vanroute.backend.models.vehicle.enums.VehicleStatus;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.DriverVehicleRepository;
import br.com.vanroute.backend.repositories.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverVehicleRepository driverVehicleRepository;
    private final DriverRepository driverRepository;
    private final IcpValidationService icpValidationService;
    private final CrlvExtractionService crlvExtractionService;

    public VehicleService(VehicleRepository vehicleRepository, DriverVehicleRepository driverVehicleRepository,
                          DriverRepository driverRepository, IcpValidationService icpValidationService,
                          CrlvExtractionService crlvExtractionService) {
        this.vehicleRepository = vehicleRepository;
        this.driverVehicleRepository = driverVehicleRepository;
        this.driverRepository = driverRepository;
        this.icpValidationService = icpValidationService;
        this.crlvExtractionService = crlvExtractionService;
    }
    //maldição
    @Transactional
    public VehicleResponseDTO addVehicle(MultipartFile crlv, UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AccessDeniedException("Só motorista pode cadastrar van"));
        try {
            icpValidationService.validateCrlvSignature(crlv);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O CRLV não passou na auditoria digital.");
        }
        CrlvExtractionService.CrlvData data = crlvExtractionService.extract(crlv);

        // RENAVAM primeiro (nunca muda); a placa pode mudar na conversao pro padrao Mercosul
        Vehicle vehicle = vehicleRepository.findByRenavam(data.renavam())
                .or(() -> vehicleRepository.findByPlate(data.plate()))
                .orElseGet(() -> vehicleRepository.save(newVehicle(data)));

        if (driverVehicleRepository.findFirstByDriverUserIdAndVehicleIdAndEndDateIsNull(driverId, vehicle.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já está vinculado a essa van");
        }
        DriverVehicle link = new DriverVehicle();
        link.setDriver(driver);
        link.setVehicle(vehicle);
        link.setStartDate(LocalDate.now());
        driverVehicleRepository.save(link);

        return toResponse(link);
    }

    public List<VehicleResponseDTO> findMyVehicles(UUID driverId) {
        if (!driverRepository.existsById(driverId)) {
            throw new AccessDeniedException("Só motorista tem van");
        }
        return driverVehicleRepository.findActiveByDriver(driverId).stream()
                .map(this::toResponse)
                .toList();
    }

    // o motorista para de usar a van; o historico fica (end_date)
    @Transactional
    public void unlinkVehicle(UUID vehicleId, UUID driverId) {
        DriverVehicle link = driverVehicleRepository.findFirstByDriverUserIdAndVehicleIdAndEndDateIsNull(driverId, vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Você não está vinculado a essa van"));
        link.setEndDate(LocalDate.now());
    }

    private Vehicle newVehicle(CrlvExtractionService.CrlvData data) {
        Vehicle vehicle = new Vehicle();
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setPlate(data.plate());
        vehicle.setRenavam(data.renavam());
        vehicle.setChassis(data.chassis());
        vehicle.setModel(data.model());
        vehicle.setYear(data.modelYear());
        vehicle.setManufactureYear(data.manufactureYear());
        vehicle.setColor(data.color());
        vehicle.setFuel(data.fuel());
        vehicle.setCategory(data.category());
        vehicle.setLicensingYear(data.licensingYear());
        vehicle.setCapacity(data.passengers());
        return vehicle;
    }

    private VehicleResponseDTO toResponse(DriverVehicle link) {
        Vehicle vehicle = link.getVehicle();
        return new VehicleResponseDTO(
                vehicle.getId(),
                vehicle.getPlate(),
                vehicle.getRenavam(),
                vehicle.getChassis(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getManufactureYear(),
                vehicle.getColor(),
                vehicle.getFuel(),
                vehicle.getCategory(),
                vehicle.getCapacity(),
                vehicle.getLicensingYear(),
                vehicle.getStatus(),
                link.getStartDate()
        );
    }
}

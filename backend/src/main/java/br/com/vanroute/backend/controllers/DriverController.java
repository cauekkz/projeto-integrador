package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.user.DriverRequestDTO;
import br.com.vanroute.backend.dtos.user.IcpExtractedInfo;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.services.DriverService;
import br.com.vanroute.backend.services.ICPValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;
    private final ICPValidationService icpValidationService;

    @Autowired
    public DriverController(DriverService driverService, ICPValidationService icpValidationService) {
        this.driverService = driverService;
        this.icpValidationService = icpValidationService;
    }

    @PostMapping(consumes = { "multipart/form-data" })
    public ResponseEntity<Driver> createDriver(@Valid @ModelAttribute DriverRequestDTO dto) {
        try {
            icpValidationService.validateDocumentSignature(dto.getDocumentPdf());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O documento CRLV/CNH não passou na auditoria digital da ICP-Brasil."); // + e.getMessage()
        }

        IcpExtractedInfo icpInfo = icpValidationService.extractDataFromDocument(dto.getDocumentPdf());
        
        Driver savedDriver = driverService.createDriver(icpInfo, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedDriver);
    }
}

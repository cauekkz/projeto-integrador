package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.user.DriverRequestDTO;
import br.com.vanroute.backend.dtos.user.IcpExtractedInfo;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.services.DriverService;
import br.com.vanroute.backend.services.IcpService;
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
    private final IcpService icpService;

    @Autowired
    public DriverController(DriverService driverService, IcpService icpService) {
        this.driverService = driverService;
        this.icpService = icpService;
    }

    @PostMapping(value = "/signup", consumes = { "multipart/form-data" })
    public ResponseEntity<?> createDriver(@Valid @ModelAttribute DriverRequestDTO dto) {
        try {
            icpService.validateDocumentSignature(dto.getDocumentPdf());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O documento CRLV/CNH não passou na auditoria digital da ICP-Brasil."); // + e.getMessage()
        }

        IcpExtractedInfo icpInfo = icpService.extractDataFromDocument(dto.getDocumentPdf());
        return ResponseEntity.status(HttpStatus.CREATED).body(icpInfo);
       // Driver savedDriver = driverService.createDriver(icpInfo, dto);
        //return ResponseEntity.status(HttpStatus.CREATED).body(savedDriver);
    }
}

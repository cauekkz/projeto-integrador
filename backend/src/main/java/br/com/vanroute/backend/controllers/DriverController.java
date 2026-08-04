package br.com.vanroute.backend.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.vanroute.backend.dtos.user.DriverRequestDTO;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.services.DocumentOcrExtractionService;
import br.com.vanroute.backend.services.DriverService;
import br.com.vanroute.backend.services.IcpValidationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/driver")
public class DriverController {

    private final DriverService driverService;
    private final IcpValidationService icpValidationService;
    private final DocumentOcrExtractionService documentOcrExtractionService;

    @Autowired
    public DriverController(
            DriverService driverService,
            IcpValidationService icpValidationService,
            DocumentOcrExtractionService documentOcrExtractionService) {
        this.driverService = driverService;
        this.icpValidationService = icpValidationService;
        this.documentOcrExtractionService = documentOcrExtractionService;
    }

    @PostMapping(value = "/signup", consumes = {"multipart/form-data"})
    public ResponseEntity<?> createDriver(@Valid @ModelAttribute DriverRequestDTO dto) {
        try {
            icpValidationService.validateCnhSignature(dto.getDocumentPdf());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A CNH não passou na auditoria digital.");
        }                                                                                       

        String cpf = documentOcrExtractionService.extractFromCnh(dto.getDocumentPdf());

        Driver savedDriver = driverService.createDriver(dto, cpf);

        return ResponseEntity.status(HttpStatus.CREATED).body("Motorista criado com sucesso");


    }

    @PostMapping(value = "/verifyCNH", consumes = {"multipart/form-data"})
    public ResponseEntity<?> verifyCNH(@Valid MultipartFile documentPdf) {
        try {
            icpValidationService.validateCnhSignature(documentPdf);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A CNH não passou na auditoria digital.");
        }

        return ResponseEntity.ok().build();
    }
    

}

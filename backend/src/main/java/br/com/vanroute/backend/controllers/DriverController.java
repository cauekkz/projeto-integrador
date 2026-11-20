package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.services.CnhParser;
import br.com.vanroute.backend.services.TripService;
import java.time.LocalDate;

import br.com.vanroute.backend.config.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import br.com.vanroute.backend.dtos.user.PixKeyDTO;
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
import br.com.vanroute.backend.services.EmailVerificationService;
import br.com.vanroute.backend.services.IcpValidationService;
import br.com.vanroute.backend.services.TermsService;
import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/driver")
public class DriverController {

    private final DriverService driverService;
    private final IcpValidationService icpValidationService;
    private final DocumentOcrExtractionService documentOcrExtractionService;
    private final EmailVerificationService emailVerificationService;
    private final TermsService termsService;

    @Autowired
    public DriverController(
            DriverService driverService,
            IcpValidationService icpValidationService,
            DocumentOcrExtractionService documentOcrExtractionService,
            EmailVerificationService emailVerificationService, TermsService termsService) {
        this.termsService = termsService;
        this.driverService = driverService;
        this.icpValidationService = icpValidationService;
        this.documentOcrExtractionService = documentOcrExtractionService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping(value = "/auth/signup", consumes = {"multipart/form-data"})
    public ResponseEntity<?> createDriver(@Valid @ModelAttribute DriverRequestDTO dto) {
        termsService.requireCurrent(dto.getAcceptedTermsVersion());
        try {
            icpValidationService.validateCnhSignature(dto.getDocumentPdf());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A CNH não passou na auditoria digital.");
        }                                                                                       

        // CPF e nome saem da CNH (nome longo fica como a CNH traz, cortado). A CNH precisa estar valida, ser D ou E e o motorista
        // ter 21 anos ou mais (CTB art. 138)
        CnhParser.CnhData cnh = documentOcrExtractionService.extractFromCnh(dto.getDocumentPdf());
        CnhParser.check(cnh, LocalDate.now(TripService.ZONE));

        Driver savedDriver = driverService.createDriver(dto, cnh.cpf(), CnhParser.nameToSave(cnh));
        termsService.accept(savedDriver.getUserId(), dto.getAcceptedTermsVersion());
        // fazer um try alguma coisa mais limpa possivel pra se isso nao funciona rmanda uma msg pro usuario reenviar o codigo no momento aparece uma msg de Redis nao funcionando (pq eu nem ligue) mas nao sei nem onde ta essa menssagem no fluxo 
        emailVerificationService.sendCodeAfterSignup("verificationEmail:email:" + savedDriver.getUser().getEmail(), savedDriver.getUser().getEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body("Motorista criado com sucesso");

    }

    @PostMapping(value = "/auth/verify-CNH", consumes = {"multipart/form-data"})
    public ResponseEntity<?> verifyCNH(@Valid MultipartFile documentPdf) {
        try {
            icpValidationService.validateCnhSignature(documentPdf);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A CNH não passou na auditoria digital.");
        }

        return ResponseEntity.ok().build();
    }

    // motorista consulta o proprio codigo de conversa, identificado pelo jwt (nunca por id na url)
    @GetMapping("/link-code")
    public ResponseEntity<Map<String, String>> getLinkCode(Authentication authentication) {
        String linkCode = driverService.getLinkCode(AuthenticatedUser.id(authentication));
        return ResponseEntity.ok(Map.of("linkCode", linkCode));
    }

    // chave Pix do motorista logado (o responsavel ve ela nas parcelas pra copiar e pagar)
    @GetMapping("/pix-key")
    public ResponseEntity<PixKeyDTO> getPixKey(Authentication authentication) {
        return ResponseEntity.ok(driverService.getPixKey(AuthenticatedUser.id(authentication)));
    }

    @PutMapping("/pix-key")
    public ResponseEntity<PixKeyDTO> setPixKey(@RequestBody(required = false) PixKeyDTO request, Authentication authentication) {
        return ResponseEntity.ok(driverService.setPixKey(AuthenticatedUser.id(authentication), request));
    }

    @DeleteMapping("/pix-key")
    public ResponseEntity<Void> removePixKey(Authentication authentication) {
        driverService.removePixKey(AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
    

}

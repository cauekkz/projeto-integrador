package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.contract.ContractDetailsResponseDTO;
import br.com.vanroute.backend.services.ContractService;
import br.com.vanroute.backend.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// criar/aceitar/recusar continuam no chat (a proposta nasce como mensagem); aqui so a consulta
@RestController
@RequestMapping("/api/contract")
public class ContractController {

    private final ContractService contractService;
    private final UserService userService;

    public ContractController(ContractService contractService, UserService userService) {
        this.contractService = contractService;
        this.userService = userService;
    }

    // so as partes do contrato (responsavel e motorista) conseguem ver
    @GetMapping("/{contractId}")
    public ResponseEntity<ContractDetailsResponseDTO> getContract(@PathVariable UUID contractId, Authentication authentication) {
        UUID userId = userService.findByCpf(authentication.getName()).get().getId();
        return ResponseEntity.ok(contractService.getContractDetails(contractId, userId));
    }
}

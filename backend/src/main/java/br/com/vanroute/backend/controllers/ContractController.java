package br.com.vanroute.backend.controllers;

import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.dtos.contract.ContractSummaryResponseDTO;
import br.com.vanroute.backend.dtos.contract.ContractDetailsResponseDTO;
import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.services.ContractService;
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

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    // contratos do usuario logado (motorista ou responsavel); ?status=ACTIVE filtra
    @GetMapping("/my-contracts")
    public ResponseEntity<List<ContractSummaryResponseDTO>> myContracts(@RequestParam(required = false) ContractStatus status,
                                                                        Authentication authentication) {
        return ResponseEntity.ok(contractService.findMyContracts(AuthenticatedUser.id(authentication), status));
    }

    // so as partes do contrato (responsavel e motorista) conseguem ver
    @GetMapping("/{contractId}")
    public ResponseEntity<ContractDetailsResponseDTO> getContract(@PathVariable UUID contractId, Authentication authentication) {
        return ResponseEntity.ok(contractService.getContractDetails(contractId, AuthenticatedUser.id(authentication)));
    }
}

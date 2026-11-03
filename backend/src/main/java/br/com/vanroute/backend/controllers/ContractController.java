package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.contract.MyContractsFilterRequestDTO;

import org.springframework.web.bind.annotation.ModelAttribute;

import org.springframework.data.web.PageableDefault;

import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.Page;

import br.com.vanroute.backend.dtos.contract.ContractSummaryResponseDTO;
import br.com.vanroute.backend.dtos.contract.ContractDetailsResponseDTO;
import br.com.vanroute.backend.dtos.contract.CancellationRequestDTO;
import br.com.vanroute.backend.dtos.contract.CancellationResponseDTO;
import br.com.vanroute.backend.services.ContractCancellationService;
import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.services.ContractService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// criar/aceitar/recusar a proposta continuam no chat (ela nasce como mensagem); aqui a consulta e o cancelamento
@RestController
@RequestMapping("/api/contract")
public class ContractController {

    private final ContractService contractService;
    private final ContractCancellationService cancellationService;

    public ContractController(ContractService contractService, ContractCancellationService cancellationService) {
        this.contractService = contractService;
        this.cancellationService = cancellationService;
    }

    // contratos do usuario logado (motorista ou responsavel), paginados, do inicio mais recente pro mais antigo.
    // Filtros opcionais: ?status=&studentId=&driverId= e &page=&size= (padrao 20)
    @GetMapping("/my-contracts")
    public ResponseEntity<Page<ContractSummaryResponseDTO>> myContracts(@ModelAttribute MyContractsFilterRequestDTO filter,
                                                                        @PageableDefault(size = 20) Pageable pageable,
                                                                        Authentication authentication) {
        return ResponseEntity.ok(contractService.findMyContracts(AuthenticatedUser.id(authentication), filter, pageable));
    }

    // pede o cancelamento com aviso previo de 30 dias: o contrato e cancelado sozinho na effectiveDate, ou antes se a
    // outra parte aceitar. O motorista com parcela vencida ha 30 dias ou mais cancela na hora
    @PostMapping("/{contractId}/cancellation")
    public ResponseEntity<CancellationResponseDTO> requestCancellation(@PathVariable UUID contractId,
                                                                       @RequestBody(required = false) CancellationRequestDTO request,
                                                                       Authentication authentication) {
        return ResponseEntity.status(201).body(cancellationService.request(contractId, AuthenticatedUser.id(authentication), request));
    }

    // a outra parte aceita antes do fim do aviso: o contrato e cancelado na hora
    @PutMapping("/{contractId}/cancellation/accept")
    public ResponseEntity<CancellationResponseDTO> acceptCancellation(@PathVariable UUID contractId, Authentication authentication) {
        return ResponseEntity.ok(cancellationService.accept(contractId, AuthenticatedUser.id(authentication)));
    }

    // quem pediu desiste do pedido
    @DeleteMapping("/{contractId}/cancellation")
    public ResponseEntity<CancellationResponseDTO> withdrawCancellation(@PathVariable UUID contractId, Authentication authentication) {
        return ResponseEntity.ok(cancellationService.withdraw(contractId, AuthenticatedUser.id(authentication)));
    }

    // so as partes do contrato (responsavel e motorista) conseguem ver
    @GetMapping("/{contractId}")
    public ResponseEntity<ContractDetailsResponseDTO> getContract(@PathVariable UUID contractId, Authentication authentication) {
        return ResponseEntity.ok(contractService.getContractDetails(contractId, AuthenticatedUser.id(authentication)));
    }
}

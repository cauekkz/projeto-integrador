package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.payment.MarkPaidRequestDTO;
import br.com.vanroute.backend.dtos.payment.PaymentResponseDTO;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import br.com.vanroute.backend.services.PaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

// parcelas: o responsavel e o motorista veem as dos seus contratos; so o motorista confirma ou cancela
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // filtros opcionais: aluno, motorista, responsavel, contrato, status (PENDING, OVERDUE, PAID, CANCELED)
    // e periodo do vencimento (from/to, AAAA-MM-DD). Ordem: vencimento mais antigo primeiro
    @GetMapping
    public ResponseEntity<Page<PaymentResponseDTO>> list(@RequestParam(required = false) UUID studentId,
                                                         @RequestParam(required = false) UUID driverId,
                                                         @RequestParam(required = false) UUID responsibleId,
                                                         @RequestParam(required = false) UUID contractId,
                                                         @RequestParam(required = false) PaymentStatus status,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                         @PageableDefault(size = 20) Pageable pageable,
                                                         Authentication authentication) {
        return ResponseEntity.ok(paymentService.list(AuthenticatedUser.id(authentication),
                new PaymentService.Filter(studentId, driverId, responsibleId, contractId, status, from, to), pageable));
    }

    // o motorista confirma o recebimento: { "method": "PIX" }
    @PutMapping("/{paymentId}/paid")
    public ResponseEntity<PaymentResponseDTO> markPaid(@PathVariable UUID paymentId, @RequestBody(required = false) MarkPaidRequestDTO request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(paymentService.markPaid(paymentId, AuthenticatedUser.id(authentication), request));
    }

    // o motorista dispensa a parcela
    @PutMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentResponseDTO> cancel(@PathVariable UUID paymentId, Authentication authentication) {
        return ResponseEntity.ok(paymentService.cancel(paymentId, AuthenticatedUser.id(authentication)));
    }
}

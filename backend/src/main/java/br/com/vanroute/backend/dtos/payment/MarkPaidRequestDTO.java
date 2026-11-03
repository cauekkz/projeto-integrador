package br.com.vanroute.backend.dtos.payment;

import br.com.vanroute.backend.models.contract.enums.PaymentMethod;

// o motorista confirma que recebeu e diz como (PIX, CASH, ...)
public record MarkPaidRequestDTO(PaymentMethod method) {
}

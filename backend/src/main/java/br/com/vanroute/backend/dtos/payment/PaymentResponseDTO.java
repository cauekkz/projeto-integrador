package br.com.vanroute.backend.dtos.payment;

import br.com.vanroute.backend.models.contract.enums.PaymentMethod;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import br.com.vanroute.backend.models.user.enums.PixKeyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// parcela de um contrato. status OVERDUE = pendente com vencimento antes de hoje.
// driverPixKey: chave do motorista pro responsavel copiar e pagar (null se ele nao cadastrou)
public record PaymentResponseDTO(
        UUID id,
        UUID contractId,
        UUID studentId,
        String studentName,
        UUID driverId,
        String driverName,
        UUID responsibleId,
        String responsibleName,
        BigDecimal value,
        LocalDate dueDate,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        LocalDateTime paidAt,
        PixKeyType driverPixKeyType,
        String driverPixKey
) {
}

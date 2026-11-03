package br.com.vanroute.backend.dtos.contract;

// pedido de cancelamento: o motivo e obrigatorio (ate 500 caracteres)
public record CancellationRequestDTO(String reason) {
}

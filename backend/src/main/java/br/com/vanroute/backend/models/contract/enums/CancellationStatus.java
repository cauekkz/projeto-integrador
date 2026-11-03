package br.com.vanroute.backend.models.contract.enums;

public enum CancellationStatus {
    PENDING, // esperando a outra parte
    ACCEPTED, // contrato cancelado
    REJECTED, // a outra parte recusou: o contrato continua
    WITHDRAWN // quem pediu desistiu
}

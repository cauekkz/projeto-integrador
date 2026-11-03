package br.com.vanroute.backend.models.contract.enums;

public enum CancellationStatus {
    PENDING, // aviso previo correndo: vale sozinho na effectiveDate
    ACCEPTED, // cancelado antes do fim do aviso: a outra parte aceitou, ou o motorista cancelou por parcela atrasada
    COMPLETED, // o aviso terminou e o contrato foi cancelado (worker)
    WITHDRAWN, // quem pediu desistiu (ou o contrato acabou antes)
    REJECTED // so historico: ninguem recusa mais
}

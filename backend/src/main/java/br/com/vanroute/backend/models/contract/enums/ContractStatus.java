package br.com.vanroute.backend.models.contract.enums;

public enum ContractStatus {
    PENDING, //pendente
    ACTIVE, // Ativo
    INACTIVE, // Inativo
    CANCELED, // Cancelado
    // SUSPENDED, // Suspenso: sem uso
    // OVERDUE // Atrasado: sem uso (atraso e da parcela, nao do contrato)
}
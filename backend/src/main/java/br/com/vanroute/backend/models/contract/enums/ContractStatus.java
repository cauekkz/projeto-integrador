package br.com.vanroute.backend.models.contract.enums;

public enum ContractStatus {
    PENDING, // proposta esperando o responsavel
    SCHEDULED, // aceito, mas ainda nao chegou a data de inicio (o worker passa pra ACTIVE no dia)
    ACTIVE, // em vigor: o aluno pode entrar nas rotas
    INACTIVE, // passou da data de fim
    CANCELED, // cancelado (proposta recusada, retirada ou expirada, ou contrato cancelado)
    // SUSPENDED, // Suspenso: sem uso
    // OVERDUE // Atrasado: sem uso (atraso e da parcela, nao do contrato)
}

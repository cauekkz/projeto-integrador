package br.com.vanroute.backend.models.user.enums;

// situacao financeira do responsavel (responsibles.financial_status): o worker diario marca OVERDUE
// e o backend volta pra UP_TO_DATE quando o motorista da baixa
public enum FinancialStatus {
    UP_TO_DATE, // Em dia
    // PENDING, // Pendente: virou UP_TO_DATE na V32
    OVERDUE, // Atrasado
    // DEFAULTING, // Inadimplente: sem uso
    // BLOCKED // Bloqueado: sem uso
}

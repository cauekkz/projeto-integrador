package br.com.vanroute.backend.models.user.enums;

// situacao financeira do responsavel no perfil (ResponsibleProfileService): calculada na hora pelas parcelas
// atrasadas, nao fica gravada (a coluna responsibles.financial_status saiu na V36)
public enum FinancialStatus {
    UP_TO_DATE, // Em dia
    // PENDING, // Pendente: virou UP_TO_DATE na V32
    OVERDUE, // Atrasado
    // DEFAULTING, // Inadimplente: sem uso
    // BLOCKED // Bloqueado: sem uso
}

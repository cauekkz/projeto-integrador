package br.com.vanroute.backend.models.user.enums;

// CNH invalida nem chega a criar o motorista, entao todo motorista e APPROVED.
// Os outros ficam comentados ate existir aprovacao/suspensao de motorista
public enum DriverApprovalStatus {
    // PENDING, // Pendente
    APPROVED, // Aprovado
    // REJECTED, // Rejeitado
    // SUSPENDED // Suspenso
}

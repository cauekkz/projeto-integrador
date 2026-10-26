package br.com.vanroute.backend.models.notification.enums;

public enum NotificationType {
    ROUTE, // Rota
    PAYMENT, // Pagamento
    OCCURRENCE, // Ocorrencia
    BOARDING, // Embarque
    SYSTEM, // Sistema
    // contrato
    CONTRACT_PROPOSED, // motorista mandou proposta (pro responsavel)
    CONTRACT_ACCEPTED, // responsavel aceitou (pro motorista)
    CONTRACT_REJECTED, // responsavel recusou (pro motorista)
    // viagem (pro responsavel)
    TRIP_STARTED, // a van saiu
    VAN_ON_THE_WAY, // o proximo ponto da van e do filho (buscar em casa ou levar pra escola/casa)
    STUDENT_BOARDED, // embarcou
    STUDENT_DELIVERED, // chegou (escola ou casa)
    STUDENT_ABSENT, // motorista marcou como ausente nessa viagem
    // falta avisada pelo responsavel (pro motorista)
    ABSENCE_REPORTED
}
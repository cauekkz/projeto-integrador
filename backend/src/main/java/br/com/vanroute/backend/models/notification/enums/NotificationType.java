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
    CONTRACT_WITHDRAWN, // motorista retirou a proposta (pro responsavel)
    CANCELLATION_REQUESTED, // uma parte pediu pra cancelar o contrato, com aviso previo (pra outra)
    CANCELLATION_REJECTED, // so historico: ninguem recusa mais (o pedido vale sozinho no fim do aviso)
    CANCELLATION_WITHDRAWN, // quem pediu desistiu (pra outra parte)
    CONTRACT_CANCELED, // contrato cancelado: pedido aceito, fim do aviso previo ou o motorista cancelou por atraso
    // gravadas pelo worker diario (worker/), pras duas partes
    CONTRACT_EXPIRED, // proposta nao respondida ate a data de inicio
    CONTRACT_ENDED, // contrato passou da data de fim
    CONTRACT_STARTED, // contrato agendado chegou na data de inicio
    // viagem (pro responsavel)
    TRIP_STARTED, // a van saiu
    VAN_ON_THE_WAY, // o proximo ponto da van e do filho (buscar em casa ou levar pra escola/casa)
    STUDENT_BOARDED, // embarcou
    STUDENT_DELIVERED, // chegou (escola ou casa)
    STUDENT_ABSENT, // motorista marcou como ausente nessa viagem
    // falta avisada pelo responsavel (pro motorista)
    ABSENCE_REPORTED,
    // aviso do motorista pra um grupo de alunos (ex.: "nao vou buscar hoje")
    DRIVER_NOTICE
}
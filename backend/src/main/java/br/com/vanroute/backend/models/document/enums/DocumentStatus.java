package br.com.vanroute.backend.models.document.enums;

public enum DocumentStatus {
    PENDING, // falta assinatura
    SIGNED, // as duas partes assinaram (o responsavel aceitou o contrato)
    REJECTED // o responsavel recusou
}

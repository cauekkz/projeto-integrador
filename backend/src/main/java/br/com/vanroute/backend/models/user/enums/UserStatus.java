package br.com.vanroute.backend.models.user.enums;

public enum UserStatus {
    ACTIVE, // Ativo
    INACTIVE, // conta excluida pelo proprio usuario (dados pessoais apagados, nao entra mais)
    // SUSPENDED, // Suspenso: sem uso (so com uma area de admin)
    // BLOCKED, // Bloqueado: sem uso (so com uma area de admin)
    CHECK_EMAIL // Verificar email
}

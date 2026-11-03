package br.com.vanroute.backend.models.user.enums;

public enum PixKeyType {
    CPF, // 11 digitos
    CNPJ, // 14 digitos
    EMAIL, // Email
    PHONE, // +55 com DDD
    RANDOM // Chave aleatoria (UUID)
}

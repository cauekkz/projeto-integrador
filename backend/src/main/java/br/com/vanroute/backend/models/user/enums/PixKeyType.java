package br.com.vanroute.backend.models.user.enums;

public enum PixKeyType {
    CPF, // 11 digitos
    CNPJ, // 14 digitos
    EMAIL, // Email
    PHONE, // +55 com DDD
    RANDOM; // Chave aleatoria (UUID)

    // nome em portugues pras mensagens de erro
    public String label() {
        return switch (this) {
            case CPF -> "CPF";
            case CNPJ -> "CNPJ";
            case EMAIL -> "e-mail";
            case PHONE -> "telefone";
            case RANDOM -> "chave aleatória";
        };
    }
}

package br.com.vanroute.backend.utils;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.user.enums.PixKeyType;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

// confere e deixa a chave Pix no formato que o banco aceita (o responsavel cola do jeito que esta guardado):
// CPF/CNPJ so digitos, e-mail minusculo, telefone +55DDDNUMERO e chave aleatoria como UUID minusculo
public final class PixKeys {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int EMAIL_MAX = 77;

    private PixKeys() {
    }

    public static String normalize(PixKeyType type, String raw) {
        if (type == null || raw == null || raw.isBlank()) {
            throw new BadRequestException("Informe o tipo e a chave Pix");
        }
        String key = raw.trim();
        String digits = key.replaceAll("\\D", "");
        String normalized = switch (type) {
            case CPF -> digits.length() == 11 && validCpf(digits) ? digits : null;
            case CNPJ -> digits.length() == 14 && validCnpj(digits) ? digits : null;
            case EMAIL -> {
                String email = key.toLowerCase(Locale.ROOT);
                yield email.length() <= EMAIL_MAX && EMAIL.matcher(email).matches() ? email : null;
            }
            case PHONE -> phone(digits);
            case RANDOM -> randomKey(key);
        };
        if (normalized == null) {
            throw new BadRequestException("Chave Pix inválida para o tipo " + type);
        }
        return normalized;
    }

    // celular ou fixo com DDD; aceita com ou sem o 55 na frente
    private static String phone(String digits) {
        String national = digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13) ? digits.substring(2) : digits;
        if ((national.length() != 10 && national.length() != 11) || national.charAt(0) == '0') {
            return null;
        }
        return "+55" + national;
    }

    private static String randomKey(String key) {
        try {
            String uuid = UUID.fromString(key).toString();
            return uuid.equalsIgnoreCase(key) ? uuid : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean validCpf(String cpf) {
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(cpf, 9, 10) == cpf.charAt(9) - '0' && checkDigit(cpf, 10, 11) == cpf.charAt(10) - '0';
    }

    private static int checkDigit(String digits, int length, int firstWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (firstWeight - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    static boolean validCnpj(String cnpj) {
        if (cnpj.chars().distinct().count() == 1) {
            return false;
        }
        int[] first = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] second = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return cnpjDigit(cnpj, first) == cnpj.charAt(12) - '0' && cnpjDigit(cnpj, second) == cnpj.charAt(13) - '0';
    }

    private static int cnpjDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }
}

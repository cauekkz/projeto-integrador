package br.com.vanroute.backend.utils;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.user.enums.PixKeyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PixKeysTest {

    @Test
    void normalizesEachTypeToWhatTheBankAccepts() {
        assertEquals("52998224725", PixKeys.normalize(PixKeyType.CPF, "529.982.247-25"));
        assertEquals("11222333000181", PixKeys.normalize(PixKeyType.CNPJ, "11.222.333/0001-81"));
        assertEquals("joao@van.com", PixKeys.normalize(PixKeyType.EMAIL, "  Joao@Van.COM "));
        assertEquals("+5511987654321", PixKeys.normalize(PixKeyType.PHONE, "(11) 98765-4321"));
        assertEquals("+5511987654321", PixKeys.normalize(PixKeyType.PHONE, "+55 11 98765-4321"));
        assertEquals("+551133334444", PixKeys.normalize(PixKeyType.PHONE, "11 3333-4444"));
        assertEquals("123e4567-e89b-12d3-a456-426614174000",
                PixKeys.normalize(PixKeyType.RANDOM, "123E4567-E89B-12D3-A456-426614174000"));
    }

    @Test
    void rejectsKeysThatDoNotMatchTheType() {
        String[][] invalid = {
                {"CPF", "529.982.247-26"}, {"CPF", "111.111.111-11"}, {"CPF", "1234"},
                {"CNPJ", "11.222.333/0001-82"}, {"CNPJ", "52998224725"},
                {"EMAIL", "joao.van.com"}, {"EMAIL", "a@b"},
                {"PHONE", "98765-4321"}, {"PHONE", "(01) 98765-4321"},
                {"RANDOM", "nao-e-uuid"}, {"RANDOM", "123e4567e89b12d3a456426614174000"},
        };
        for (String[] key : invalid) {
            assertThrows(BadRequestException.class, () -> PixKeys.normalize(PixKeyType.valueOf(key[0]), key[1]), key[0] + " " + key[1]);
        }
        assertThrows(BadRequestException.class, () -> PixKeys.normalize(null, "52998224725"));
        assertThrows(BadRequestException.class, () -> PixKeys.normalize(PixKeyType.CPF, "  "));
    }
}

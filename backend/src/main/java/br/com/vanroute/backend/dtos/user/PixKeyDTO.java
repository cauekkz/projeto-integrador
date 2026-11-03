package br.com.vanroute.backend.dtos.user;

import br.com.vanroute.backend.models.user.enums.PixKeyType;

// chave Pix do motorista; na resposta, a chave ja vem normalizada (pronta pra copiar)
public record PixKeyDTO(PixKeyType type, String key) {
}

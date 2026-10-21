package br.com.vanroute.backend.dtos.chat;

import br.com.vanroute.backend.models.chat.enums.AttachmentType;
import jakarta.validation.constraints.AssertTrue;

// o que o usuario pode mandar; o tipo e sempre REGULAR_CHAT (proposta/aceite/recusa so o sistema gera)
public record ChatMessageRequestDTO(
    String content,
    AttachmentType attachmentType,
    String attachmentUrl
) {
    @AssertTrue(message = "A mensagem deve conter texto e/ou um anexo válido.")
    public boolean isValidContentOrAttachment() {
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasAttachment = attachmentType != null;
        return hasContent || hasAttachment;
    }
}

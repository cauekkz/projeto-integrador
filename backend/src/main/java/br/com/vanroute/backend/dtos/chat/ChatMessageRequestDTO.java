package br.com.vanroute.backend.dtos.chat;

import br.com.vanroute.backend.models.chat.enums.AttachmentType;
import br.com.vanroute.backend.models.chat.enums.MessageType;
import jakarta.validation.constraints.AssertTrue;

public record ChatMessageRequestDTO(
    String content,
    AttachmentType attachmentType,
    String attachmentUrl,
    MessageType messageType
) {
    @AssertTrue(message = "A mensagem deve conter texto e/ou um anexo válido.")
    public boolean isValidContentOrAttachment() {
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasAttachment = attachmentType != null;
        return hasContent || hasAttachment;
    }
}

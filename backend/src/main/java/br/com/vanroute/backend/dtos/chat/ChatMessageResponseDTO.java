package br.com.vanroute.backend.dtos.chat;

import java.util.UUID;

import br.com.vanroute.backend.models.chat.ChatMessage;
import br.com.vanroute.backend.models.chat.enums.AttachmentType;
import br.com.vanroute.backend.models.chat.enums.MessageType;

import java.time.LocalDateTime;

public record ChatMessageResponseDTO(
                UUID id,
                UUID senderId,
                String content,
                String attachmentUrl,
                AttachmentType attachmentType,
                LocalDateTime sentAt,
                MessageType messageType,
                String payload,
                // quando o outro leu (null = nao lida: ✓; preenchida: ✓✓)
                LocalDateTime readAt) {

        public static ChatMessageResponseDTO from(ChatMessage message) {
                return new ChatMessageResponseDTO(
                                message.getId(),
                                message.getSender().getId(),
                                message.getContent(),
                                message.getAttachmentUrl(),
                                message.getAttachmentType(),
                                message.getSentAt(),
                                message.getMessageType(),
                                message.getPayload(),
                                message.getReadAt());
        }

}

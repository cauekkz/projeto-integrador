package br.com.vanroute.backend.dtos.chat;

import java.util.UUID;

import br.com.vanroute.backend.models.chat.ChatMessage;
import br.com.vanroute.backend.models.chat.enums.AttachmentType;

import java.time.LocalDateTime;

public record ChatMessageResponseDTO(
                UUID id,
                UUID senderId,
                String content,
                String attachmentUrl,
                AttachmentType attachmentType,
                LocalDateTime sentAt) {

        public static ChatMessageResponseDTO from(ChatMessage message) {
                return new ChatMessageResponseDTO(
                                message.getId(),
                                message.getSender().getId(),
                                message.getContent(),
                                message.getAttachmentUrl(),
                                message.getAttachmentType(),
                                message.getSentAt());
        }

}

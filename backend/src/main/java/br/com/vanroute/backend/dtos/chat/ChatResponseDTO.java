package br.com.vanroute.backend.dtos.chat;

import java.time.LocalDateTime;
import java.util.UUID;
//thanks chatgpt
// conversa na lista. unreadCount = mensagens do outro que eu ainda nao li
public record ChatResponseDTO(
    UUID id,
    UUID otherUserId,
    String otherUserName,
    String otherUserPhotoKey,
    LocalDateTime createdAt,
    long unreadCount
) {
    public static ChatResponseDTO from(br.com.vanroute.backend.models.chat.Chat chat, UUID principalId, long unreadCount) {
        br.com.vanroute.backend.models.user.User otherUser = chat.getUserOne().getId().equals(principalId) ? chat.getUserTwo() : chat.getUserOne();
        return new ChatResponseDTO(chat.getId(), otherUser.getId(), otherUser.getName(), otherUser.getPhotoKey(), chat.getCreatedAt(),
                unreadCount);
    }
}

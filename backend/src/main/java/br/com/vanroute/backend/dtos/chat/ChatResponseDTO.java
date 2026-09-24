package br.com.vanroute.backend.dtos.chat;

import java.time.LocalDateTime;
import java.util.UUID;
//thanks chatgpt
public record ChatResponseDTO(
    UUID id,
    UUID otherUserId,
    String otherUserName,
    LocalDateTime createdAt
) {
    public static ChatResponseDTO from(br.com.vanroute.backend.models.chat.Chat chat, String principalCpf) {
        br.com.vanroute.backend.models.user.User otherUser = chat.getUserOne().getCpf().equals(principalCpf) ? chat.getUserTwo() : chat.getUserOne();
        return new ChatResponseDTO(chat.getId(), otherUser.getId(), otherUser.getName(), chat.getCreatedAt());
    }
}

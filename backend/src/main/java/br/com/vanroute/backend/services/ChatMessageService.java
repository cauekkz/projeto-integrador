package br.com.vanroute.backend.services;

import org.springframework.stereotype.Service;
import br.com.vanroute.backend.repositories.ChatMessageRepository;
import br.com.vanroute.backend.repositories.ChatRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import br.com.vanroute.backend.models.chat.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
@Service
public class ChatMessageService {
    
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;

    public ChatMessageService(ChatMessageRepository chatMessageRepository, ChatService chatService) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatService = chatService;
    }
    public Page<ChatMessageResponseDTO> getMessages(UUID chatId, String cpf, Pageable pageable) {
        chatService.validateChatAccess(chatId, cpf);
        Page<ChatMessage> messages = chatMessageRepository.findByChatIdOrderBySentAtAsc(chatId, pageable);
        return messages.map(ChatMessageResponseDTO::from);
    }
    
}

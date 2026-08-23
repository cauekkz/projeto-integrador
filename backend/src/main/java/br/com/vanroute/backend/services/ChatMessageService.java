package br.com.vanroute.backend.services;

import org.springframework.stereotype.Service;
import br.com.vanroute.backend.repositories.ChatMessageRepository;
import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import br.com.vanroute.backend.dtos.chat.ChatMessageRequestDTO;
import br.com.vanroute.backend.models.chat.ChatMessage;
import br.com.vanroute.backend.models.chat.enums.AttachmentType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Service
public class ChatMessageService {
    
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;
    private final UserService userService;

    public ChatMessageService(ChatMessageRepository chatMessageRepository, ChatService chatService, UserService userService) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatService = chatService;
        this.userService = userService;
    }
    public Page<ChatMessageResponseDTO> getMessages(UUID chatId, String cpf, Pageable pageable) {
        chatService.validateChatAccess(chatId, cpf);
        Page<ChatMessage> messages = chatMessageRepository.findByChatIdOrderBySentAtAsc(chatId, pageable);
        return messages.map(ChatMessageResponseDTO::from);
    }
    
    public ChatMessageResponseDTO sendMessage(UUID chatId, String cpf, ChatMessageRequestDTO request) {
        chatService.validateChatAccess(chatId, cpf);
        
        br.com.vanroute.backend.models.chat.Chat chat = chatService.getChatById(chatId);
        br.com.vanroute.backend.models.user.User sender = userService.findByCpf(cpf).orElseThrow(() -> new RuntimeException("User not found"));
            
        ChatMessage message = new ChatMessage();
        message.setChat(chat);
        message.setSender(sender);
        message.setContent(request.content());
        message.setAttachmentType(request.attachmentType()); // configura storage de arquivos
        message.setAttachmentUrl(request.attachmentUrl()); 
        message.setMessageType(request.messageType());
        message = chatMessageRepository.save(message);
        return ChatMessageResponseDTO.from(message);
    }
    
}

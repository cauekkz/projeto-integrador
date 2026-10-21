package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.chat.enums.MessageType;

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
    public Page<ChatMessageResponseDTO> getMessages(UUID chatId, UUID userId, Pageable pageable) {
        chatService.validateChatAccess(chatId, userId);
        Page<ChatMessage> messages = chatMessageRepository.findByChatIdOrderBySentAtAsc(chatId, pageable);
        return messages.map(ChatMessageResponseDTO::from);
    }
    
    public ChatMessageResponseDTO sendMessage(UUID chatId, UUID userId, ChatMessageRequestDTO request) {
        return save(chatId, userId, request.content(), request.attachmentType(), request.attachmentUrl(), MessageType.REGULAR_CHAT, null);
    }

    // mensagens geradas pelo fluxo de contrato (PROPOSAL, APPROVAL, REJECTION); nao ficam expostas na API
    public ChatMessageResponseDTO sendSystemMessage(UUID chatId, UUID userId, String content, MessageType type, String payload) {
        return save(chatId, userId, content, null, null, type, payload);
    }

    private ChatMessageResponseDTO save(UUID chatId, UUID userId, String content, AttachmentType attachmentType,
                                        String attachmentUrl, MessageType type, String payload) {
        chatService.validateChatAccess(chatId, userId);
        
        br.com.vanroute.backend.models.chat.Chat chat = chatService.getChatById(chatId);
        br.com.vanroute.backend.models.user.User sender = userService.findById(userId).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User not found"));
            
        ChatMessage message = new ChatMessage();
        message.setChat(chat);
        message.setSender(sender);
        message.setContent(content);
        message.setAttachmentType(attachmentType); // maldito jeito
        message.setAttachmentUrl(attachmentUrl);
        message.setMessageType(type);
        message.setPayload(payload);
        message = chatMessageRepository.save(message);
        return ChatMessageResponseDTO.from(message);
    }
    
}

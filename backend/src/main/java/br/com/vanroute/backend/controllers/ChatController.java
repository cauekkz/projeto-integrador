package br.com.vanroute.backend.controllers;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import br.com.vanroute.backend.dtos.chat.ChatResponseDTO;
import br.com.vanroute.backend.dtos.chat.ChatMessageRequestDTO;
import br.com.vanroute.backend.services.ChatMessageService;
import br.com.vanroute.backend.services.ChatService;
import jakarta.validation.Valid;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatMessageService chatMessageService;
    private final ChatService chatService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatController(
            ChatMessageService chatMessageService, 
            ChatService chatService,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.chatMessageService = chatMessageService;
        this.chatService = chatService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Page<ChatResponseDTO>> getActiveChats(@PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        String cpf = authentication.getName();
        return ResponseEntity.ok(chatService.getUserChats(cpf, pageable));
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<Page<ChatMessageResponseDTO>> getMessages(@PathVariable UUID chatId, @PageableDefault(size = 30) Pageable pageable, Authentication authentication) {
        chatService.validateChatAccess(chatId, authentication.getName());
        String cpf = authentication.getName();
        return ResponseEntity.ok(chatMessageService.getMessages(chatId, cpf, pageable));
    }   

    @PostMapping("/{chatId}/messages")
    public ResponseEntity<ChatMessageResponseDTO> sendMessage(@PathVariable UUID chatId, @Valid @RequestBody ChatMessageRequestDTO request, Authentication authentication) {
        
        String cpf = authentication.getName();
        
        ChatMessageResponseDTO msg = chatMessageService.sendMessage(chatId, cpf, request);
        
        try {
            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.convertAndSend("chat-" + chatId, json);
        } catch (Exception e) {
            log.error("Failed to publish message to Redis for chat: {}", chatId, e);
        }
        
        return ResponseEntity.status(201).body(msg);
    }

}
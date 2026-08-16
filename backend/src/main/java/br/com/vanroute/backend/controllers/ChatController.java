package br.com.vanroute.backend.controllers;

import java.net.URI;
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
import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import br.com.vanroute.backend.services.ChatMessageService;
import br.com.vanroute.backend.services.ChatService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatService chatService;
    public ChatController(ChatMessageService chatMessageService, ChatService chatService) {
        this.chatMessageService = chatMessageService;
        this.chatService = chatService;
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<Page<ChatMessageResponseDTO>> getMessages( @PathVariable UUID chatId, @PageableDefault(size = 30) Pageable pageable, Authentication authentication) {
        chatService.validateChatAccess(chatId, authentication.getName());
        String cpf = authentication.getName();
        return ResponseEntity.ok(chatMessageService.getMessages(chatId, cpf, pageable));
    }

}
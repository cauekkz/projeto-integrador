package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.chat.Chat;
import br.com.vanroute.backend.dtos.chat.ChatResponseDTO;
import br.com.vanroute.backend.repositories.ChatRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;

    public ChatService(ChatRepository chatRepository, UserRepository userRepository) {
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
    }

    public Chat createOrGetChat(UUID userA, UUID userB) {
        if (userA.equals(userB)) {
            throw new IllegalArgumentException("Um chat precisa de dois usuários distintos.");
        }

        // como no bd eu nao crie index pra comparar (1,2) (2,1) eu faço sempre a ordem crescente
        UUID userOneId = userA.compareTo(userB) < 0 ? userA : userB;
        UUID userTwoId = userA.compareTo(userB) < 0 ? userB : userA;

        return chatRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId)
                .orElseGet(() -> {
                    Chat newChat = new Chat();
                    newChat.setUserOne(userRepository.getReferenceById(userOneId));
                    newChat.setUserTwo(userRepository.getReferenceById(userTwoId));
                    return chatRepository.save(newChat);
                });
    }

    public void validateChatAccess(UUID chatId, String cpf) {
        UUID userId = userRepository.findByCpf(cpf).orElseThrow(() -> new RuntimeException("Usuário não encontrado")).getId();
        if (!chatRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new IllegalArgumentException("Você não tem acesso a este chat.");
        }
    }

    public Chat getChatById(UUID chatId) {
        return chatRepository.findById(chatId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chat não encontrado"));
    }
    
    public Page<ChatResponseDTO> getUserChats(String cpf, Pageable pageable) {
        UUID userId = userRepository.findByCpf(cpf).orElseThrow(() -> new RuntimeException("Usuário não encontrado")).getId();
        return chatRepository.findChatsByUserIdOrderByRecentMessage(userId, pageable)
                .map(chat -> ChatResponseDTO.from(chat, cpf));
    }
}

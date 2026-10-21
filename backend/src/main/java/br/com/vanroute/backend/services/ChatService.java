package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.chat.Chat;
import br.com.vanroute.backend.dtos.chat.ChatResponseDTO;
import br.com.vanroute.backend.repositories.ChatRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.List;
import br.com.vanroute.backend.models.user.User;

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

        // como no bd eu nao crie index pra comparar (1,2) (2,1) eu faço sempre a ordem
        // crescente
        // compara pela string (ordem de bytes sem sinal, igual ao Postgres); UUID.compareTo usa long com sinal
        // e violava o CHECK (user_one_id < user_two_id) para metade dos pares
        boolean aFirst = userA.toString().compareTo(userB.toString()) < 0;
        UUID userOneId = aFirst ? userA : userB;
        UUID userTwoId = aFirst ? userB : userA;

        return chatRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId)
                .orElseGet(() -> {
                    Chat newChat = new Chat();
                    newChat.setUserOne(userRepository.getReferenceById(userOneId));
                    newChat.setUserTwo(userRepository.getReferenceById(userTwoId));
                    return chatRepository.save(newChat);
                });
    }

    public void validateChatAccess(UUID chatId, UUID userId) {
        if (!chatRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new IllegalArgumentException("Você não tem acesso a este chat.");
        }
    }

    public Chat getChatById(UUID chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chat não encontrado"));
    }

    public Page<ChatResponseDTO> getUserChats(UUID userId, Pageable pageable) {
        return chatRepository.findChatsByUserIdOrderByRecentMessage(userId, pageable)
                .map(chat -> ChatResponseDTO.from(chat, userId));
    }

    public void UsersInChat(UUID chatId, UUID requesterId, UUID userId) {
        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new RuntimeException("Chat não encontrado"));
        User userOne = chat.getUserOne();
        User userTwo = chat.getUserTwo();
        boolean requesterInChat = userOne.getId().equals(requesterId) ||
                userTwo.getId().equals(requesterId);

        boolean otherInChat = userOne.getId().equals(userId) ||
                userTwo.getId().equals(userId);
        if (!requesterInChat || !otherInChat) {
            throw new IllegalArgumentException("Você não tem acesso a este chat.");
        }

    }
}

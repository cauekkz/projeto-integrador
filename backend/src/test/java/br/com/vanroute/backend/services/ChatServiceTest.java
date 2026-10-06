package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.chat.Chat;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.ChatRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import br.com.vanroute.backend.dtos.chat.ChatResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatServiceTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatService chatService;

    private Chat mockChat;
    private User userOne;
    private User userTwo;

    @BeforeEach
    void setUp() {
        userOne = new User();
        userOne.setId(UUID.randomUUID());
        userOne.setCpf("12345678901");
        userOne.setName("Naruto");

        userTwo = new User();
        userTwo.setId(UUID.randomUUID());
        userTwo.setCpf("09876543210");
        userTwo.setName("Sasuke");

        mockChat = new Chat();
        mockChat.setId(UUID.randomUUID());
        mockChat.setUserOne(userOne);
        mockChat.setUserTwo(userTwo);
    }

    @Test
    void shouldValidateChatAccessSuccess() {
        when(userRepository.findByCpf("12345678901")).thenReturn(Optional.of(userOne));
        when(chatRepository.existsByChatIdAndUserId(mockChat.getId(), userOne.getId())).thenReturn(true);

        assertDoesNotThrow(() -> chatService.validateChatAccess(mockChat.getId(), "12345678901"));
        
        verify(userRepository, times(1)).findByCpf("12345678901");
        verify(chatRepository, times(1)).existsByChatIdAndUserId(mockChat.getId(), userOne.getId());
    }

    @Test
    void shouldThrowExceptionWhenChatAccessDenied() {
        when(userRepository.findByCpf("12345678901")).thenReturn(Optional.of(userOne));
        when(chatRepository.existsByChatIdAndUserId(mockChat.getId(), userOne.getId())).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> chatService.validateChatAccess(mockChat.getId(), "12345678901"));

        assertEquals("Você não tem acesso a este chat.", exception.getMessage());
    }

    @Test
    void shouldGetUserChatsSuccessfully() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Chat> chatPage = new PageImpl<>(Collections.singletonList(mockChat));
        
        when(userRepository.findByCpf("12345678901")).thenReturn(Optional.of(userOne));
        when(chatRepository.findChatsByUserIdOrderByRecentMessage(userOne.getId(), pageable)).thenReturn(chatPage);

        Page<ChatResponseDTO> result = chatService.getUserChats("12345678901", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Sasuke", result.getContent().get(0).otherUserName());
    }
}

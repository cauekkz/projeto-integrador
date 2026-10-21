package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.chat.ChatMessageRequestDTO;
import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import br.com.vanroute.backend.models.chat.Chat;
import br.com.vanroute.backend.models.chat.ChatMessage;
import br.com.vanroute.backend.models.chat.enums.MessageType;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    private Chat mockChat;
    private User senderUser;

    @BeforeEach
    void setUp() {
        senderUser = new User();
        senderUser.setId(UUID.randomUUID());
        senderUser.setCpf("12345678901");
        senderUser.setName("Luffy");

        mockChat = new Chat();
        mockChat.setId(UUID.randomUUID());
        mockChat.setUserOne(senderUser);
        mockChat.setUserTwo(new User());
    }

    @Test
    void shouldSendMessageSuccessfully() {
        ChatMessageRequestDTO requestDTO = new ChatMessageRequestDTO(
                "Hello, pirate king!",
                null,
                null
        );

        when(chatService.getChatById(mockChat.getId())).thenReturn(mockChat);
        when(userService.findById(senderUser.getId())).thenReturn(Optional.of(senderUser));

        ChatMessage savedMessage = new ChatMessage();
        savedMessage.setId(UUID.randomUUID());
        savedMessage.setContent("Hello, pirate king!");
        savedMessage.setSender(senderUser);
        savedMessage.setChat(mockChat);
        savedMessage.setMessageType(MessageType.REGULAR_CHAT);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        ChatMessageResponseDTO response = chatMessageService.sendMessage(
                mockChat.getId(), senderUser.getId(), requestDTO);

        assertNotNull(response);
        assertEquals("Hello, pirate king!", response.content());
        assertEquals(senderUser.getId(), response.senderId());
        verify(chatService).validateChatAccess(mockChat.getId(), senderUser.getId());
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
    }

    @Test
    void userMessageIsAlwaysRegularChatWithoutPayload() {
        when(chatService.getChatById(mockChat.getId())).thenReturn(mockChat);
        when(userService.findById(senderUser.getId())).thenReturn(Optional.of(senderUser));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponseDTO response = chatMessageService.sendMessage(
                mockChat.getId(), senderUser.getId(), new ChatMessageRequestDTO("oi", null, null));

        assertEquals(MessageType.REGULAR_CHAT, response.messageType());
        assertNull(response.payload());
    }

    @Test
    void shouldThrowWhenUserHasNoChatAccess() {
        ChatMessageRequestDTO requestDTO = new ChatMessageRequestDTO(
                "Hello",
                null,
                null
        );

        doThrow(new IllegalArgumentException("Você não tem acesso a este chat."))
                .when(chatService).validateChatAccess(mockChat.getId(), senderUser.getId());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> chatMessageService.sendMessage(mockChat.getId(), senderUser.getId(), requestDTO)
        );

        assertEquals("Você não tem acesso a este chat.", exception.getMessage());
        verify(chatMessageRepository, never()).save(any());
    }
}

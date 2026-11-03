package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.contract.ContractDetailsResponseDTO;
import br.com.vanroute.backend.models.chat.Chat;
import br.com.vanroute.backend.models.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractChatServiceTest {

    @Mock
    private ChatService chatService;
    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private ContractService contractService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ContractChatService service;

    private static User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Chat chatBetween(UUID a, UUID b) {
        Chat chat = new Chat();
        chat.setUserOne(user(a));
        chat.setUserTwo(user(b));
        return chat;
    }

    @Test
    void contractOfAnotherConversationCannotBeAcceptedHere() {
        UUID responsibleId = UUID.randomUUID();
        UUID driverA = UUID.randomUUID();
        UUID driverB = UUID.randomUUID();
        UUID chatWithB = UUID.randomUUID();
        UUID contractWithA = UUID.randomUUID();
        when(chatService.getChatById(chatWithB)).thenReturn(chatBetween(responsibleId, driverB));
        ContractDetailsResponseDTO details = mock(ContractDetailsResponseDTO.class);
        when(details.driverId()).thenReturn(driverA);
        when(details.responsibleId()).thenReturn(responsibleId);
        when(contractService.getContractDetails(contractWithA, responsibleId)).thenReturn(details);

        assertThrows(AccessDeniedException.class, () -> service.accept(chatWithB, contractWithA, responsibleId));
        verify(contractService, never()).acceptContract(any(), any());
        verify(chatMessageService, never()).sendSystemMessage(any(), any(), any(), any(), any());
    }
}

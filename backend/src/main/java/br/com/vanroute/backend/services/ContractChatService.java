package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import br.com.vanroute.backend.dtos.contract.ContractDetailsResponseDTO;
import br.com.vanroute.backend.dtos.contract.ContractProposalRequestDTO;
import br.com.vanroute.backend.models.chat.Chat;
import br.com.vanroute.backend.models.chat.enums.MessageType;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// proposta, aceite, recusa e retirada pelo chat. Cada operacao e uma transacao so: o contrato, a mensagem do chat
// (o card) e a notificacao sao gravados juntos ou nenhum e gravado. O WebSocket so recebe depois do commit
@Service
public class ContractChatService {

    private final ChatService chatService;
    private final ChatMessageService chatMessageService;
    private final ContractService contractService;
    private final NotificationService notificationService;

    public ContractChatService(ChatService chatService, ChatMessageService chatMessageService, ContractService contractService,
                               NotificationService notificationService) {
        this.chatService = chatService;
        this.chatMessageService = chatMessageService;
        this.contractService = contractService;
        this.notificationService = notificationService;
    }

    public record Proposal(ChatMessageResponseDTO message, UUID userDriverContractId, ContractDetailsResponseDTO contract) {
    }

    @Transactional
    public Proposal propose(UUID chatId, UUID driverId, ContractProposalRequestDTO request) {
        chatService.UsersInChat(chatId, driverId, request.responsibleId());
        UserDriverContract udc = contractService.createContract(driverId, request);
        String payload = "{\"userDriverContractId\": \"" + udc.getId() + "\", \"contractId\": \"" + udc.getContract().getId() + "\"}";
        ChatMessageResponseDTO msg = chatMessageService.sendSystemMessage(chatId, driverId, "Proposta de Contrato",
                MessageType.PROPOSAL, payload);
        chatMessageService.publishAfterCommit(chatId, msg);
        ContractDetailsResponseDTO details = contractService.getContractDetails(udc.getContract().getId(), driverId);
        notificationService.send(List.of(request.responsibleId()), NotificationType.CONTRACT_PROPOSED, "Nova proposta de contrato",
                details.driverName() + " enviou uma proposta de contrato para " + details.studentName(),
                Map.of("chatId", chatId, "contractId", details.contractId()));
        return new Proposal(msg, udc.getId(), details);
    }

    @Transactional
    public void accept(UUID chatId, UUID contractId, UUID responsibleId) {
        checkContractOfChat(chatId, contractId, responsibleId);
        contractService.acceptContract(contractId, responsibleId);
        ContractDetailsResponseDTO details = contractService.getContractDetails(contractId, responsibleId);
        notificationService.send(List.of(details.driverId()), NotificationType.CONTRACT_ACCEPTED, "Contrato aceito",
                details.responsibleName() + " aceitou o contrato de " + details.studentName(),
                Map.of("chatId", chatId, "contractId", contractId));
        systemMessage(chatId, responsibleId, "Contrato Aceito", MessageType.APPROVAL, contractId);
    }

    @Transactional
    public void reject(UUID chatId, UUID contractId, UUID responsibleId) {
        checkContractOfChat(chatId, contractId, responsibleId);
        contractService.rejectContract(contractId, responsibleId);
        ContractDetailsResponseDTO details = contractService.getContractDetails(contractId, responsibleId);
        notificationService.send(List.of(details.driverId()), NotificationType.CONTRACT_REJECTED, "Contrato recusado",
                details.responsibleName() + " recusou o contrato de " + details.studentName(),
                Map.of("chatId", chatId, "contractId", contractId));
        systemMessage(chatId, responsibleId, "Contrato Recusado", MessageType.REJECTION, contractId);
    }

    // o motorista retira a proposta que ainda esta pendente (REJECTION: o front ja trata como proposta encerrada)
    @Transactional
    public void withdraw(UUID chatId, UUID contractId, UUID driverId) {
        checkContractOfChat(chatId, contractId, driverId);
        contractService.withdrawContract(contractId, driverId);
        ContractDetailsResponseDTO details = contractService.getContractDetails(contractId, driverId);
        notificationService.send(List.of(details.responsibleId()), NotificationType.CONTRACT_WITHDRAWN, "Proposta retirada",
                details.driverName() + " retirou a proposta de contrato de " + details.studentName(),
                Map.of("chatId", chatId, "contractId", contractId));
        systemMessage(chatId, driverId, "Proposta Retirada", MessageType.REJECTION, contractId);
    }

    private void systemMessage(UUID chatId, UUID senderId, String content, MessageType type, UUID contractId) {
        ChatMessageResponseDTO msg = chatMessageService.sendSystemMessage(chatId, senderId, content, type,
                "{\"contractId\": \"" + contractId + "\"}");
        chatMessageService.publishAfterCommit(chatId, msg);
    }

    // o contrato tem que ser entre as duas pessoas dessa conversa: senao o card iria parar no chat de outra pessoa
    // (ex.: o responsavel aceita o contrato do motorista A usando o chatId da conversa com o motorista B)
    private void checkContractOfChat(UUID chatId, UUID contractId, UUID userId) {
        chatService.validateChatAccess(chatId, userId);
        Chat chat = chatService.getChatById(chatId);
        ContractDetailsResponseDTO details = contractService.getContractDetails(contractId, userId);
        List<UUID> chatUsers = List.of(chat.getUserOne().getId(), chat.getUserTwo().getId());
        if (!chatUsers.contains(details.driverId()) || !chatUsers.contains(details.responsibleId())) {
            throw new AccessDeniedException("Esse contrato não é dessa conversa");
        }
    }
}

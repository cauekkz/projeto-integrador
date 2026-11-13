package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.services.StudentService;
import br.com.vanroute.backend.dtos.student.ChildSummaryResponseDTO;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import br.com.vanroute.backend.dtos.chat.ChatResponseDTO;
import br.com.vanroute.backend.dtos.chat.ChatMessageRequestDTO;
import br.com.vanroute.backend.services.ChatMessageService;
import br.com.vanroute.backend.services.ChatService;
import jakarta.validation.Valid;
import br.com.vanroute.backend.dtos.contract.ContractProposalRequestDTO;

import br.com.vanroute.backend.services.ContractChatService;
import br.com.vanroute.backend.services.ChatReadService;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatService chatService;
    private final StudentService studentService;
    private final ContractChatService contractChatService;
    private final ChatReadService chatReadService;

    public ChatController(ChatMessageService chatMessageService, ChatService chatService, StudentService studentService,
                          ContractChatService contractChatService, ChatReadService chatReadService) {
        this.chatReadService = chatReadService;
        this.chatMessageService = chatMessageService;
        this.chatService = chatService;
        this.studentService = studentService;
        this.contractChatService = contractChatService;
    }

    @GetMapping
    public ResponseEntity<Page<ChatResponseDTO>> getActiveChats(@PageableDefault(size = 10) Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.getUserChats(AuthenticatedUser.id(authentication), pageable));
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<Page<ChatMessageResponseDTO>> getMessages(@PathVariable UUID chatId,
            @PageableDefault(size = 30) Pageable pageable, Authentication authentication) {
        return ResponseEntity.ok(chatMessageService.getMessages(chatId, AuthenticatedUser.id(authentication), pageable));
    }

    // marca a conversa como lida ate agora (chame ao abrir a conversa e ao receber mensagem com ela aberta).
    // marca as mensagens do outro como lidas; ele recebe {"type":"read",...} no WebSocket do chat
    @PutMapping("/{chatId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID chatId, Authentication authentication) {
        chatReadService.markRead(chatId, AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{chatId}/messages")
    public ResponseEntity<ChatMessageResponseDTO> sendMessage(@PathVariable UUID chatId,
            @Valid @RequestBody ChatMessageRequestDTO request, Authentication authentication) {

        ChatMessageResponseDTO msg = chatMessageService.sendMessage(chatId, AuthenticatedUser.id(authentication), request);
        chatMessageService.publish(chatId, msg);
        return ResponseEntity.status(201).body(msg);
    }


    //rota apenas de driver,
    //ich bin müde
    // filhos do pai da conversa, pro motorista escolher o aluno da proposta
    @GetMapping("/{chatId}/students")
    public ResponseEntity<List<ChildSummaryResponseDTO>> getChatPartnerChildren(@PathVariable UUID chatId,
                                                                                Authentication authentication) {
        return ResponseEntity.ok(studentService.getChildrenOfChatPartner(chatId, AuthenticatedUser.id(authentication)));
    }

    @PostMapping("/{chatId}/contract")
    public ResponseEntity<Object> createContract(@PathVariable UUID chatId,
        @Valid @RequestBody ContractProposalRequestDTO request, Authentication authentication) {
        ContractChatService.Proposal proposal = contractChatService.propose(chatId, AuthenticatedUser.id(authentication), request);
        return ResponseEntity.status(201).body(Map.of(
                "message", proposal.message(),
                "userDriverContractId", proposal.userDriverContractId(),
                "contract", proposal.contract()));
    }

    // o contractId vem do payload da mensagem PROPOSAL do chat; so o responsavel do contrato aceita, e o contrato
    // tem que ser dessa conversa
    @PutMapping("/{chatId}/contracts/{contractId}/accept")
    public ResponseEntity<Void> acceptContract(@PathVariable UUID chatId, @PathVariable UUID contractId, Authentication authentication) {
        contractChatService.accept(chatId, contractId, AuthenticatedUser.id(authentication));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{chatId}/contracts/{contractId}/reject")
    public ResponseEntity<Void> rejectContract(@PathVariable UUID chatId, @PathVariable UUID contractId, Authentication authentication) {
        contractChatService.reject(chatId, contractId, AuthenticatedUser.id(authentication));
        return ResponseEntity.ok().build();
    }

    // o motorista retira a proposta que ainda esta pendente
    @PutMapping("/{chatId}/contracts/{contractId}/withdraw")
    public ResponseEntity<Void> withdrawContract(@PathVariable UUID chatId, @PathVariable UUID contractId, Authentication authentication) {
        contractChatService.withdraw(chatId, contractId, AuthenticatedUser.id(authentication));
        return ResponseEntity.ok().build();
    }

}
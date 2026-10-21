package br.com.vanroute.backend.controllers;

import java.util.List;
import br.com.vanroute.backend.services.StudentService;
import br.com.vanroute.backend.dtos.student.ChildSummaryResponseDTO;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import br.com.vanroute.backend.dtos.chat.ChatResponseDTO;
import br.com.vanroute.backend.dtos.chat.ChatMessageRequestDTO;
import br.com.vanroute.backend.services.ChatMessageService;
import br.com.vanroute.backend.services.ChatService;
import jakarta.validation.Valid;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.vanroute.backend.dtos.contract.ContractProposalRequestDTO;
import  br.com.vanroute.backend.services.ContractService;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatMessageService chatMessageService;
    private final ChatService chatService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ContractService contractService;
    private final StudentService studentService;
    
    public ChatController(
            ChatMessageService chatMessageService,
            ChatService chatService,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            ContractService contractService,
            StudentService studentService) {
        this.chatMessageService = chatMessageService;
        this.chatService = chatService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.contractService = contractService;
        this.studentService = studentService;
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

    @PostMapping("/{chatId}/messages")
    public ResponseEntity<ChatMessageResponseDTO> sendMessage(@PathVariable UUID chatId,
            @Valid @RequestBody ChatMessageRequestDTO request, Authentication authentication) {

        ChatMessageResponseDTO msg = chatMessageService.sendMessage(chatId, AuthenticatedUser.id(authentication), request);

        try {
            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.convertAndSend("chat-" + chatId, json);
        } catch (Exception e) {
            log.error("Failed to publish message to Redis for chat: {}", chatId, e);
        }

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
        UUID driverId = AuthenticatedUser.id(authentication);
        chatService.UsersInChat(chatId, driverId, request.responsibleId());
        
        br.com.vanroute.backend.models.contract.UserDriverContract udc = contractService.createContract(driverId, request);
        

        
        String payloadStr = "{\"userDriverContractId\": \"" + udc.getId() + "\", \"contractId\": \"" + udc.getContract().getId() + "\"}";
        ChatMessageResponseDTO msg = chatMessageService.sendSystemMessage(chatId, driverId, "Proposta de Contrato",
                br.com.vanroute.backend.models.chat.enums.MessageType.PROPOSAL, payloadStr);
        try {
            redisTemplate.convertAndSend("chat-" + chatId, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("Failed to publish message to Redis for chat: {}", chatId, e);
        }

        return ResponseEntity.status(201).body(Map.of(
                "message", msg,
                "userDriverContractId", udc.getId(),
                "contract", contractService.getContractDetails(udc.getContract().getId(), driverId)));
    }

    @PutMapping("/{chatId}/contracts/{contractId}/accept")
    //essa maldita recebera o id do contrato e id do chat da url ent tem que manda certin ent so manda o krl do jwt no header
    //ai o macaco se pergunta "mas gotao de onde eu vou tirar o id dessas porra??" ent gotao o chat meu irmao je é pra ter faz tempo ne ja tem um get pra pikas como essa, blz ai o id do contrato na linha 108 ele gurda junto com o id da tabela que guarda o krl tudo ele guarda o id do contrato tbm ent é dele
    //alias front é bom oia com o id dessa merda vc consegue puxar o contrato o documento todas as realação pra mostra pro responsbie hj chega amanha é foco 100% dnv
    public ResponseEntity<Void> acceptContract(@PathVariable UUID chatId, @PathVariable UUID contractId, Authentication authentication) {
        UUID responsibleId = AuthenticatedUser.id(authentication);
        chatService.validateChatAccess(chatId, responsibleId);
        
        contractService.acceptContract(contractId, responsibleId);
        
        String payloadStr = "{\"contractId\": \"" + contractId + "\"}";
        ChatMessageResponseDTO msg = chatMessageService.sendSystemMessage(chatId, responsibleId, "Contrato Aceito",
                br.com.vanroute.backend.models.chat.enums.MessageType.APPROVAL, payloadStr);
        try {
            redisTemplate.convertAndSend("chat-" + chatId, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("Error to publish", e);
        }
        return ResponseEntity.ok().build();
    }
    //hemoglobina expande explode 

    @PutMapping("/{chatId}/contracts/{contractId}/reject")
    public ResponseEntity<Void> rejectContract(@PathVariable UUID chatId, @PathVariable UUID contractId, Authentication authentication) {
        UUID responsibleId = AuthenticatedUser.id(authentication);
        chatService.validateChatAccess(chatId, responsibleId);
        
        contractService.rejectContract(contractId, responsibleId);
        
        String payloadStr = "{\"contractId\": \"" + contractId + "\"}";
        ChatMessageResponseDTO msg = chatMessageService.sendSystemMessage(chatId, responsibleId, "Contrato Recusado",
                br.com.vanroute.backend.models.chat.enums.MessageType.REJECTION, payloadStr);
        try {
            redisTemplate.convertAndSend("chat-" + chatId, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("Error to publish", e);
        }
        return ResponseEntity.ok().build();
    }

    

}
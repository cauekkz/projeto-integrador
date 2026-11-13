package br.com.vanroute.backend.services;

import br.com.vanroute.backend.repositories.ChatMessageRepository;
import br.com.vanroute.backend.repositories.ChatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// "lida" do chat: chat_messages.read_at (null = nao lida). Ler a conversa marca as mensagens do OUTRO como lidas,
// e o outro recebe {"type":"read","chatId","userId","readAt"} no WebSocket do chat (as mensagens dele viram ✓✓)
@Service
public class ChatReadService {

    private static final Logger log = LoggerFactory.getLogger(ChatReadService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRepository chatRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatReadService(ChatMessageRepository chatMessageRepository, ChatRepository chatRepository,
                           RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRepository = chatRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // o usuario abriu (ou esta com) a conversa aberta
    @Transactional
    public void markRead(UUID chatId, UUID userId) {
        if (!chatRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new AccessDeniedException("Você não tem acesso a este chat.");
        }
        readAll(chatId, userId);
    }

    // marca como lidas as mensagens do outro participante; so avisa se alguma mudou
    @Transactional
    public void readAll(UUID chatId, UUID userId) {
        LocalDateTime now = LocalDateTime.now(TripService.ZONE);
        if (chatMessageRepository.markReadFromOther(chatId, userId, now) > 0) {
            publishAfterCommit(chatId, userId, now);
        }
    }

    public long unreadCount(UUID chatId, UUID userId) {
        return chatMessageRepository.countUnreadFromOther(chatId, userId);
    }

    private void publishAfterCommit(UUID chatId, UUID userId, LocalDateTime readAt) {
        Runnable publish = () -> {
            try {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("type", "read");
                event.put("chatId", chatId);
                event.put("userId", userId);
                event.put("readAt", readAt);
                redisTemplate.convertAndSend("chat-" + chatId, objectMapper.writeValueAsString(event));
            } catch (Exception e) {
                log.warn("Falha ao avisar a leitura do chat {}", chatId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}

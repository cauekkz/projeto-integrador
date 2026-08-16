package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.chat.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByChatIdOrderBySentAtAsc(UUID chatId, Pageable pageable);

}

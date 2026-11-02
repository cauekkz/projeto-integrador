package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.chat.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByChatIdOrderBySentAtAsc(UUID chatId, Pageable pageable);

    // o arquivo foi enviado como anexo em algum chat do qual o usuario participa
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM ChatMessage m " +
            "WHERE m.attachmentUrl = :fileKey AND (m.chat.userOne.id = :userId OR m.chat.userTwo.id = :userId)")
    boolean existsAttachmentVisibleTo(@Param("fileKey") String fileKey, @Param("userId") UUID userId);

}

package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.chat.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByChatIdOrderBySentAtAsc(UUID chatId, Pageable pageable);

    // o arquivo foi enviado como anexo em algum chat do qual o usuario participa
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM ChatMessage m " +
            "WHERE m.attachmentUrl = :fileKey AND (m.chat.userOne.id = :userId OR m.chat.userTwo.id = :userId)")
    boolean existsAttachmentVisibleTo(@Param("fileKey") String fileKey, @Param("userId") UUID userId);

    // o usuario leu a conversa: as mensagens do outro (texto, anexo e as do sistema) ficam lidas
    @Modifying
    @Query("UPDATE ChatMessage m SET m.readAt = :now WHERE m.chat.id = :chatId AND m.sender.id <> :userId AND m.readAt IS NULL")
    int markReadFromOther(@Param("chatId") UUID chatId, @Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.chat.id = :chatId AND m.sender.id <> :userId AND m.readAt IS NULL")
    long countUnreadFromOther(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

}

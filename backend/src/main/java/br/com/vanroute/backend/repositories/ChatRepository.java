package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.chat.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {
    Optional<Chat> findByUserOneIdAndUserTwoId(UUID userOneId, UUID userTwoId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Chat c WHERE c.id = :chatId " +
            "AND (c.userOne.id = :userId OR c.userTwo.id = :userId)")
    boolean existsByChatIdAndUserId(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    @Query("SELECT c FROM Chat c LEFT JOIN ChatMessage m ON m.chat = c WHERE c.userOne.id = :userId OR c.userTwo.id = :userId GROUP BY c ORDER BY MAX(m.sentAt) DESC")
    org.springframework.data.domain.Page<Chat> findChatsByUserIdOrderByRecentMessage(@Param("userId") UUID userId, Pageable pageable);
}

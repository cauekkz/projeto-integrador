package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.chat.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {
    Optional<Chat> findByUserOneIdAndUserTwoId(UUID userOneId, UUID userTwoId);

}

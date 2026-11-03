package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.user.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    // so o id do motorista dono do codigo de conversa (o resgate do convite nao precisa do resto)
    @Query("SELECT d.userId FROM Driver d WHERE d.linkCode = :linkCode")
    Optional<UUID> findUserIdByLinkCode(@Param("linkCode") String linkCode);
}

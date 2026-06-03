package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.user.RolesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RolesRepository extends JpaRepository<RolesEntity, UUID> {
    Optional<RolesEntity> findByNome(String nome);

}

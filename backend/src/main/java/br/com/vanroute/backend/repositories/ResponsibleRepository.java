package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.user.Responsible;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResponsibleRepository extends JpaRepository<Responsible, UUID> {
}

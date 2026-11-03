package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.user.TermsAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, UUID> {

    boolean existsByUserIdAndVersion(UUID userId, String version);

    List<TermsAcceptance> findByUserIdOrderByAcceptedAt(UUID userId);
}

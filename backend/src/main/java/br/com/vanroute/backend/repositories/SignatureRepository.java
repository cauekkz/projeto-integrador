package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.document.Signature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SignatureRepository extends JpaRepository<Signature, UUID> {
    List<Signature> findByDocumentIdOrderBySignedAtAsc(UUID documentId);
}

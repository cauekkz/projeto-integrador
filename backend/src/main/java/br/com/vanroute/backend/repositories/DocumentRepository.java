package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.document.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.vanroute.backend.models.document.enums.DocumentEntityType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findFirstByEntityTypeAndEntityId(DocumentEntityType entityType, UUID entityId);

    // documento de contrato: so as partes do contrato (motorista e responsavel) podem baixar
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Document d, UserDriverContract u " +
            "WHERE d.url = :fileKey AND d.entityType = br.com.vanroute.backend.models.document.enums.DocumentEntityType.CONTRACT " +
            "AND u.contract.id = d.entityId AND (u.responsible.userId = :userId OR u.driver.userId = :userId)")
    boolean existsContractDocumentVisibleTo(@Param("fileKey") String fileKey, @Param("userId") UUID userId);
}

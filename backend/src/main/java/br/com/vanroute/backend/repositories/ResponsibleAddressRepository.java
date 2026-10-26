package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.address.ResponsibleAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResponsibleAddressRepository extends JpaRepository<ResponsibleAddress, UUID> {
}

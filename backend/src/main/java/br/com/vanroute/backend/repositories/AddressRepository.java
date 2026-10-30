package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.address.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    // ja tem coordenada: reaproveita sem chamar a API de mapas
    Optional<Address> findFirstByAddressKeyAndLatitudeIsNotNullOrderByIdAsc(String addressKey);
}

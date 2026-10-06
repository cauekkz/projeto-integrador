package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.user.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByLinkCode(String linkCode);
    Optional<Driver> findByUserCpf(String cpf);
}

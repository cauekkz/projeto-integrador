package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.contract.UserDriverContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserDriverContractRepository extends JpaRepository<UserDriverContract, UUID> {
}

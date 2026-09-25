package br.com.vanroute.backend.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.vanroute.backend.models.contract.Contract;

public interface ContractRepository extends JpaRepository<Contract, UUID> {
    
    
}

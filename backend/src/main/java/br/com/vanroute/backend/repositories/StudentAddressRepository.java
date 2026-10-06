package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.student.StudentAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentAddressRepository extends JpaRepository<StudentAddress, UUID> {
}

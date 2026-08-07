package br.com.vanroute.backend.dtos.user;

import br.com.vanroute.backend.models.student.enums.RelationType;

import java.time.LocalDate;

public record StudentRequestDTO(String name, String notes, LocalDate birthDate, RelationType relationType){
}

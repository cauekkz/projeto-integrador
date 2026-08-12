package br.com.vanroute.backend.dtos.student;


import br.com.vanroute.backend.models.student.enums.RelationType;

public record  AllStudentsFilterRequestDTO(
    RelationType relationType,
    Boolean isAdmin,
    String studentName


) {}                            
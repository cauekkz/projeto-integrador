package br.com.vanroute.backend.services;

import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.vanroute.backend.repositories.StudentResponsibleRepository;

@Service
public class StudentResponsibleService {

    private final StudentResponsibleRepository studentResponsibleRepository;

    public StudentResponsibleService(
            StudentResponsibleRepository studentResponsibleRepository) {
        this.studentResponsibleRepository = studentResponsibleRepository;
    }

    public boolean isAdmin(UUID responsibleId, UUID studentId) {
        return studentResponsibleRepository
                .existsByResponsible_UserIdAndStudent_IdAndIsAdminTrue(
                        responsibleId,
                        studentId
                );
    }
}

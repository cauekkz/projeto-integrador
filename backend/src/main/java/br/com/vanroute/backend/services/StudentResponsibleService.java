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

    public boolean isAdminByCpf(String cpf, UUID studentId) {
        return studentResponsibleRepository                                                                                                                                                                                                                                                                                                                                     
                .existsByResponsible_User_CpfAndStudent_IdAndIsAdminTrue(
                        cpf,
                        studentId
                );
    }

    public boolean isAdmin(UUID responsibleId, UUID studentId) {
        return studentResponsibleRepository
                .existsByResponsible_IdAndStudent_IdAndIsAdminTrue(
                        responsibleId,
                        studentId
                );
    }
}

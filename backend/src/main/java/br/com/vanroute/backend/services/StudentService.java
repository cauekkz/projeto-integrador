package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.StudentRequestDTO;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.repositories.StudentRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final ResponsibleRepository responsibleRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;

    public StudentService(StudentRepository studentRepository, ResponsibleRepository responsibleRepository, StudentResponsibleRepository studentResponsibleRepository) {
        this.studentRepository = studentRepository;
        this.responsibleRepository = responsibleRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
    }

    public StudentResponsible createStudentWithRelation(StudentRequestDTO dto, String cpf){
        Student student = new Student();
        student.setName(dto.name());
        student.setNotes(dto.notes());
        student.setBirthDate(dto.birthDate());
        studentRepository.save(student);
        
        StudentResponsible studentResponsible = new StudentResponsible();
        studentResponsible.setStudent(student);
        studentResponsible.setRelationType(dto.relationType());
        Responsible responsible = responsibleRepository.findByUserCpf(cpf)
                .orElseThrow(() -> new RuntimeException("Responsible not found"));
        studentResponsible.setResponsible(responsible);
       return studentResponsibleRepository.save(studentResponsible);
    }
}

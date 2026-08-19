package br.com.vanroute.backend.services;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import br.com.vanroute.backend.dtos.user.StudentRequestDTO;
import br.com.vanroute.backend.dtos.student.AllStudentsFilterRequestDTO;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.repositories.StudentRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.specifications.student.StudentResponsibleSpecification;

import org.springframework.data.domain.Page;
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final ResponsibleRepository responsibleRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;


    public StudentService(StudentRepository studentRepository, ResponsibleRepository responsibleRepository, StudentResponsibleRepository studentResponsibleRepository       ) {
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
            studentResponsible.setAdmin(true);

            //obrigado felipe já ia fica maluco
            Responsible responsible = responsibleRepository.findByUserCpf(cpf)
                    .orElseThrow(() -> new RuntimeException("Responsible not found"));
            studentResponsible.setResponsible(responsible);
        return studentResponsibleRepository.save(studentResponsible);
        }

        public Page<Student> getAllStudentsResponsible(AllStudentsFilterRequestDTO filter,Pageable pageable,String cpf) {
            Specification<StudentResponsible> specification = StudentResponsibleSpecification.withFilters(cpf, filter);
            Page<StudentResponsible> result = studentResponsibleRepository.findAll(specification, pageable);
            return result.map(StudentResponsible::getStudent);
        }


    //ns se a melhor coisa é fazer isso nesse service mas fds nao consigo pensa num service diferente pra isso, talvez um especifico pra isso mas nao sei



}

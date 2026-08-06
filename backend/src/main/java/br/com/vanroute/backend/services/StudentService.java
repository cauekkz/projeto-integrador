package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.repositories.StudentRepository;
import org.springframework.stereotype.Service;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student registerStudent(Student studentDto){
        Student student = new Student();
        student.setBirthDate(studentDto.getBirthDate());
        student.setNotes(studentDto.getNotes());
        return null;
    }
}

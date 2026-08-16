package br.com.vanroute.backend.services;

import java.security.SecureRandom;
import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.vanroute.backend.dtos.student.StudentLinkRequestDTO;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.repositories.StudentRepository;

import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.utils.CodeGenerator;

@Service
public class StudentLinkService {


    private static final String REDIS_KEY_PREFIX = "student:link:";


    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CodeGenerator codeGenerator;

    private final StudentResponsibleRepository studentResponsibleRepository;
    private final StudentRepository studentRepository;
private final ResponsibleRepository responsibleRepository;

    public StudentLinkService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, StudentResponsibleRepository studentResponsibleRepository, StudentRepository studentRepository,ResponsibleRepository responsibleRepository, CodeGenerator codeGenerator) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.codeGenerator = codeGenerator;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.studentRepository = studentRepository;
        this.responsibleRepository = responsibleRepository;
    }

    public String generateStudentCodeToLink(StudentLinkRequestDTO dto, String cpf) {
        String code;

        if (!studentResponsibleRepository.existsByResponsible_User_CpfAndStudent_IdAndIsAdminTrue(cpf, dto.id())) {
            throw new RuntimeException(
                    "O Responsavel não tem essa permissão");
        }

        try {
            String json = objectMapper.writeValueAsString(dto);
            while (true) {
                code = codeGenerator.generateCode();
                // alias eu nao lembro se fiz essa verificação no bglh de envia email dps tem q
                // ver. é que é do Redis ne ai quem me ensino foi o brabo
                Boolean result = redisTemplate.opsForValue().setIfAbsent(
                        REDIS_KEY_PREFIX + code,
                        json,
                        Duration.ofMinutes(15));
                if (Boolean.TRUE.equals(result)) {
                    break;
                }
            }
            return code;

        } catch (JsonProcessingException e) {

            throw new RuntimeException(
                    "Erro ao serializar os dados do vínculo"

            );
        }
    }

    public void createStudentResponsibleRelation(String code, String cpf) {

        String storedJson = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + code);
        if (storedJson == null) {
            throw new IllegalArgumentException("Código incorreto ou expirado.");
        }

        try {
            StudentLinkRequestDTO dto = objectMapper.readValue(storedJson, StudentLinkRequestDTO.class);

            Student student = studentRepository.getReferenceById(dto.id());

            Responsible responsible = responsibleRepository.findByUserCpf(cpf).orElseThrow(() -> new RuntimeException(
                    "Responsavel não encontrado"));

            StudentResponsible studentResponsible = new StudentResponsible();

            studentResponsible.setStudent(student);
            studentResponsible.setResponsible(responsible);
            studentResponsible.setRelationType(dto.relationType());

            // studentResponsible.setAdmin(false);

            studentResponsibleRepository.save(studentResponsible);

            redisTemplate.delete(REDIS_KEY_PREFIX + code);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Código incorreto ou expirado.");
        }

    }


}
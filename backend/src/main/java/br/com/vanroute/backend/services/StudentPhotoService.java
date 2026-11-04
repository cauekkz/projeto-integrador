package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// foto do aluno, igual a foto de perfil: 1) URL de upload  2) o app sobe a imagem direto no MinIO  3) grava o fileKey.
// So o responsavel admin troca ou tira. Quem ve: os responsaveis do aluno e o motorista que tem ou teve contrato
// com ele (StorageService.generateDownloadUrl)
@Service
public class StudentPhotoService {

    private final StudentResponsibleRepository studentResponsibleRepository;
    private final StorageService storageService;

    public StudentPhotoService(StudentResponsibleRepository studentResponsibleRepository, StorageService storageService) {
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.storageService = storageService;
    }

    public StorageService.PresignedUrlResponse uploadUrl(UUID studentId, UUID userId, String fileName, String contentType) {
        admin(studentId, userId);
        return storageService.generateStudentPhotoUploadUrl(studentId, fileName, contentType);
    }

    @Transactional
    public String setPhoto(UUID studentId, UUID userId, String fileKey) {
        Student student = admin(studentId, userId).getStudent();
        if (fileKey == null || !studentId.equals(StorageService.studentOfPhoto(fileKey))) {
            throw new BadRequestException("Envie a foto pela URL de upload da foto deste aluno");
        }
        // existe e tem no maximo 10 MB
        storageService.checkUploaded(fileKey);
        String old = student.getPhotoKey();
        student.setPhotoKey(fileKey);
        if (old != null && !old.equals(fileKey)) {
            deleteAfterCommit(old);
        }
        return fileKey;
    }

    @Transactional
    public void removePhoto(UUID studentId, UUID userId) {
        Student student = admin(studentId, userId).getStudent();
        String old = student.getPhotoKey();
        student.setPhotoKey(null);
        if (old != null) {
            deleteAfterCommit(old);
        }
    }

    private StudentResponsible admin(UUID studentId, UUID userId) {
        StudentResponsible link = studentResponsibleRepository.findByResponsible_UserIdAndStudent_Id(userId, studentId)
                .orElseThrow(() -> new AccessDeniedException("Responsável não vinculado a esse aluno"));
        if (!link.isAdmin()) {
            throw new AccessDeniedException("Só o responsável admin troca a foto do aluno");
        }
        return link;
    }

    private void deleteAfterCommit(String fileKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storageService.delete(fileKey);
                }
            });
        } else {
            storageService.delete(fileKey);
        }
    }
}

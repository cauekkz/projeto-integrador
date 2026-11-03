package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.ResponsibleProfileResponseDTO;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;
import br.com.vanroute.backend.repositories.ChatRepository;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.PaymentRepository;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

// perfil do responsavel pro motorista: so de quem ele conversa ou tem contrato
@Service
public class ResponsibleProfileService {

    private final ResponsibleRepository responsibleRepository;
    private final DriverRepository driverRepository;
    private final ChatRepository chatRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final PaymentRepository paymentRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;

    public ResponsibleProfileService(ResponsibleRepository responsibleRepository, DriverRepository driverRepository,
                                     ChatRepository chatRepository, UserDriverContractRepository userDriverContractRepository,
                                     PaymentRepository paymentRepository, StudentResponsibleRepository studentResponsibleRepository) {
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.responsibleRepository = responsibleRepository;
        this.driverRepository = driverRepository;
        this.chatRepository = chatRepository;
        this.userDriverContractRepository = userDriverContractRepository;
        this.paymentRepository = paymentRepository;
    }

    // o motorista (com chat ou contrato) ou o proprio responsavel. No proprio perfil aparecem todos os contratos
    // e todas as parcelas atrasadas dele; pro motorista, so os filhos, as parcelas e a situacao com ele
    @Transactional(readOnly = true)
    public ResponsibleProfileResponseDTO profile(UUID responsibleId, UUID driverId) {
        boolean self = responsibleId.equals(driverId);
        if (!self && !driverRepository.existsById(driverId)) {
            throw new AccessDeniedException("Só o motorista ou o próprio responsável vê esse perfil");
        }
        Responsible responsible = responsibleRepository.findById(responsibleId)
                .orElseThrow(() -> new EntityNotFoundException("Responsável não encontrado"));
        if (self) {
            return ownProfile(responsible);
        }
        if (!chatRepository.existsBetween(driverId, responsibleId)
                && !userDriverContractRepository.existsByDriverUserIdAndResponsibleUserId(driverId, responsibleId)) {
            throw new AccessDeniedException("Você só vê o perfil de um responsável com quem conversa ou tem contrato");
        }
        // so o que e entre os dois: atraso com outros motoristas nao aparece (o motorista nao e credor dessas parcelas)
        long overdueWithDriver = paymentRepository.countOverdueBetween(driverId, responsibleId, LocalDate.now(TripService.ZONE));
        return new ResponsibleProfileResponseDTO(
                responsibleId,
                responsible.getUser().getName(),
                responsible.getUser().getPhotoKey(),
                responsible.getUser().getPhone(),
                overdueWithDriver > 0 ? FinancialStatus.OVERDUE : FinancialStatus.UP_TO_DATE,
                overdueWithDriver,
                userDriverContractRepository.findAllOfUser(responsibleId).stream()
                        .filter(udc -> udc.getDriver().getUserId().equals(driverId))
                        .map(udc -> {
                            StudentResponsible link = studentResponsibleRepository
                                    .findByResponsible_UserIdAndStudent_Id(responsibleId, udc.getStudent().getId()).orElse(null);
                            return new ResponsibleProfileResponseDTO.StudentItem(udc.getStudent().getId(), udc.getStudent().getName(),
                                    udc.getContract().getId(), udc.getContract().getStatus(),
                                    link == null ? null : link.getRelationType(), link != null && link.isAdmin());
                        })
                        .toList());
    }

    private ResponsibleProfileResponseDTO ownProfile(Responsible responsible) {
        UUID id = responsible.getUserId();
        long overdue = paymentRepository.countOverdueOfResponsible(id, LocalDate.now(TripService.ZONE));
        return new ResponsibleProfileResponseDTO(id, responsible.getUser().getName(), responsible.getUser().getPhotoKey(),
                responsible.getUser().getPhone(), overdue > 0 ? FinancialStatus.OVERDUE : FinancialStatus.UP_TO_DATE, overdue,
                userDriverContractRepository.findAllOfUser(id).stream()
                        .map(udc -> {
                            StudentResponsible link = studentResponsibleRepository
                                    .findByResponsible_UserIdAndStudent_Id(id, udc.getStudent().getId()).orElse(null);
                            return new ResponsibleProfileResponseDTO.StudentItem(udc.getStudent().getId(), udc.getStudent().getName(),
                                    udc.getContract().getId(), udc.getContract().getStatus(),
                                    link == null ? null : link.getRelationType(), link != null && link.isAdmin());
                        })
                        .toList());
    }
}

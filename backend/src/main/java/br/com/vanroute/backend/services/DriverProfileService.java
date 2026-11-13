package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.rating.RatingRequestDTO;
import br.com.vanroute.backend.dtos.rating.RatingResponseDTO;
import br.com.vanroute.backend.dtos.user.DriverProfileResponseDTO;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.rating.Rating;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.repositories.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// perfil do motorista pro responsavel (com a avaliacao) e a nota que o responsavel da pra ele
@Service
public class DriverProfileService {

    // quem ja fechou contrato com o motorista (ativo ou que ja acabou) avalia; proposta pendente ou recusada, nao
    static final List<ContractStatus> CAN_RATE = List.of(ContractStatus.ACTIVE, ContractStatus.INACTIVE);

    private final DriverRepository driverRepository;
    private final ResponsibleRepository responsibleRepository;
    private final RatingRepository ratingRepository;
    private final ChatRepository chatRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final DriverSchoolsRepository driverSchoolsRepository;
    private final DriverVehicleRepository driverVehicleRepository;

    public DriverProfileService(DriverRepository driverRepository, ResponsibleRepository responsibleRepository,
                                RatingRepository ratingRepository, ChatRepository chatRepository,
                                UserDriverContractRepository userDriverContractRepository,
                                DriverSchoolsRepository driverSchoolsRepository, DriverVehicleRepository driverVehicleRepository) {
        this.driverRepository = driverRepository;
        this.responsibleRepository = responsibleRepository;
        this.ratingRepository = ratingRepository;
        this.chatRepository = chatRepository;
        this.userDriverContractRepository = userDriverContractRepository;
        this.driverSchoolsRepository = driverSchoolsRepository;
        this.driverVehicleRepository = driverVehicleRepository;
    }

    // ve o perfil: o proprio motorista, ou um responsavel que ja conversa ou tem contrato com ele
    @Transactional(readOnly = true)
    public DriverProfileResponseDTO profile(UUID driverId, UUID viewerId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado"));
        if (!driverId.equals(viewerId) && !chatRepository.existsBetween(driverId, viewerId)
                && !userDriverContractRepository.existsByDriverUserIdAndResponsibleUserId(driverId, viewerId)) {
            throw new AccessDeniedException("Você só vê o perfil de um motorista com quem conversa ou tem contrato");
        }
        Object[] summary = ratingRepository.summaryOf(driverId).get(0);
        Double average = summary[0] == null ? null : Math.round(((Number) summary[0]).doubleValue() * 10) / 10.0;
        long count = ((Number) summary[1]).longValue();
        return new DriverProfileResponseDTO(
                driverId,
                driver.getUser().getName(),
                driver.getUser().getPhotoKey(),
                driver.getUser().getCreatedAt() == null ? null : driver.getUser().getCreatedAt().toLocalDate(),
                driverSchoolsRepository.findByDriverWithSchool(driverId).stream()
                        .map(link -> new DriverProfileResponseDTO.SchoolItem(link.getSchool().getId(), link.getSchool().getName()))
                        .toList(),
                driverVehicleRepository.findActiveByDriver(driverId).stream()
                        .map(link -> new DriverProfileResponseDTO.VehicleItem(link.getVehicle().getModel(),
                                link.getVehicle().getColor(), link.getVehicle().getPlate()))
                        .toList(),
                average,
                count,
                ratingRepository.findByResponsibleUserIdAndDriverUserId(viewerId, driverId).map(DriverProfileService::toResponse).orElse(null),
                ratingRepository.findTop10ByDriverUserIdOrderByDateDesc(driverId).stream().map(DriverProfileService::toResponse).toList());
    }

    // uma avaliacao por responsavel por motorista: mandar de novo troca a nota
    @Transactional
    public RatingResponseDTO rate(UUID driverId, RatingRequestDTO request, UUID responsibleId) {
        Responsible responsible = responsibleRepository.findById(responsibleId)
                .orElseThrow(() -> new AccessDeniedException("Só responsável avalia motorista"));
        if (!userDriverContractRepository.existsByDriverUserIdAndResponsibleUserIdAndContractStatusIn(driverId, responsibleId, CAN_RATE)) {
            throw new AccessDeniedException("Só avalia quem tem ou já teve contrato com esse motorista");
        }
        Rating rating = ratingRepository.findByResponsibleUserIdAndDriverUserId(responsibleId, driverId).orElseGet(() -> {
            Rating created = new Rating();
            created.setResponsible(responsible);
            created.setDriver(driverRepository.getReferenceById(driverId));
            return created;
        });
        rating.setScore(request.score());
        rating.setComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().trim());
        rating.setDate(LocalDateTime.now(TripService.ZONE));
        return toResponse(ratingRepository.save(rating));
    }

    @Transactional
    public void deleteRating(UUID driverId, UUID responsibleId) {
        ratingRepository.delete(ratingRepository.findByResponsibleUserIdAndDriverUserId(responsibleId, driverId)
                .orElseThrow(() -> new EntityNotFoundException("Você não avaliou esse motorista")));
    }

    private static RatingResponseDTO toResponse(Rating rating) {
        String name = rating.getResponsible().getUser().getName();
        return new RatingResponseDTO(rating.getScore(), rating.getComment(), rating.getDate(), name == null ? null : name.trim().split("\\s+")[0]);
    }
}

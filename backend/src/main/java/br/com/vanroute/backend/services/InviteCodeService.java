package br.com.vanroute.backend.services;

import org.springframework.security.access.AccessDeniedException;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.models.user.Driver;
import java.util.UUID;
import br.com.vanroute.backend.models.chat.Chat;
import org.springframework.transaction.annotation.Transactional;
@Service
public class InviteCodeService {

    private final DriverRepository driverRepository;
    private final ResponsibleRepository responsibleRepository;
    private final ChatService chatService;

    public InviteCodeService(DriverRepository driverRepository, ResponsibleRepository responsibleRepository, ChatService chatService) {
        this.driverRepository = driverRepository;
        this.responsibleRepository = responsibleRepository;
        this.chatService = chatService;
    }
    @Transactional
    public UUID redeem(String code, UUID responsibleId) {
        //TODO depois trocar pra query pegar so o id e nao o objeto inteiro
        Driver driver = driverRepository.findByLinkCode(code).orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado"));
        if (!responsibleRepository.existsById(responsibleId)) {
            throw new AccessDeniedException("Só responsável pode usar código de convite");
        }

        Chat chat = chatService.createOrGetChat(driver.getUserId(), responsibleId);

        return chat.getId();
    }
}

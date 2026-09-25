package br.com.vanroute.backend.services;

import org.springframework.stereotype.Service;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
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
    public UUID redeem(String code,String cpf) {
        //TODO depois trocar pra query pegar so o id e nao o objeto inteiro
        Driver driver = driverRepository.findByLinkCode(code).orElseThrow(() -> new RuntimeException("Motorista não encontrado"));
        Responsible responsible = responsibleRepository.findByUserCpf(cpf).orElseThrow(() -> new RuntimeException("Responsável não encontrado"));

        UUID driverId = driver.getUserId();
        UUID responsibleId = responsible.getUserId();
        
        Chat chat = chatService.createOrGetChat(driverId, responsibleId);

        return chat.getId();
    }
}

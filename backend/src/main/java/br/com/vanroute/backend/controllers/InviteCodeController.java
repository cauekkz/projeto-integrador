package br.com.vanroute.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.vanroute.backend.services.InviteCodeService;
import br.com.vanroute.backend.dtos.inviteCode.InviteCodeRequestDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/invite-code")
public class InviteCodeController {

    private final InviteCodeService inviteCodeService;

    public InviteCodeController(InviteCodeService inviteCodeService) {
        this.inviteCodeService = inviteCodeService;
    }

    @PostMapping("/redeem")
    public ResponseEntity<UUID> redeem(@Valid @RequestBody InviteCodeRequestDTO inviteCodeRequestDTO, Authentication authentication) {
        String cpf = authentication.getName();
        UUID chatId = inviteCodeService.redeem(inviteCodeRequestDTO.inviteCode(), cpf);
        return ResponseEntity.created(URI.create("/api/chats/" + chatId+ "/messages")).body(chatId);
    }
    
}

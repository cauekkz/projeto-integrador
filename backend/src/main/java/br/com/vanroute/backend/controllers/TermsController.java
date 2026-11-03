package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.services.TermsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Termos de Uso e Politica de Privacidade
@RestController
@RequestMapping("/api/terms")
public class TermsController {

    private final TermsService termsService;

    public TermsController(TermsService termsService) {
        this.termsService = termsService;
    }

    // publico (a tela de cadastro mostra antes da conta existir): { version, content (markdown) }
    @GetMapping
    public TermsService.Terms current() {
        return termsService.current();
    }

    // o usuario logado aceita: { "version": "1" }. 204; 400 se nao for a versao atual
    @PostMapping("/accept")
    public ResponseEntity<Void> accept(@RequestBody(required = false) Map<String, String> body, Authentication authentication) {
        termsService.accept(AuthenticatedUser.id(authentication), body == null ? null : body.get("version"));
        return ResponseEntity.noContent().build();
    }
}

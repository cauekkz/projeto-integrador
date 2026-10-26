package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.notification.NotificationResponseDTO;
import br.com.vanroute.backend.services.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

// notificacoes do usuario logado; em tempo real chegam pelo WebSocket /notifications
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // mais novas primeiro; ?unread=true so as nao lidas
    @GetMapping
    public Page<NotificationResponseDTO> list(@RequestParam(defaultValue = "false") boolean unread,
                                              @PageableDefault(size = 20) Pageable pageable,
                                              Authentication authentication) {
        return notificationService.list(AuthenticatedUser.id(authentication), unread, pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("count", notificationService.unreadCount(AuthenticatedUser.id(authentication)));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID notificationId, Authentication authentication) {
        notificationService.markRead(notificationId, AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
}

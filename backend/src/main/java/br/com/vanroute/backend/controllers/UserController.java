package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.user.UserMeResponseDTO;
import br.com.vanroute.backend.dtos.user.UpdatePhotoRequestDTO;
import br.com.vanroute.backend.dtos.user.UpdateUser;
import br.com.vanroute.backend.services.StorageService;
import java.util.Map;
import br.com.vanroute.backend.models.user.User;//sem DTO por enquanto
import br.com.vanroute.backend.services.UserService;
import br.com.vanroute.backend.services.AccountDeletionService;
import br.com.vanroute.backend.services.DataExportService;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final DataExportService dataExportService;

    public UserController(UserService userService, AccountDeletionService accountDeletionService, DataExportService dataExportService) {
        this.dataExportService = dataExportService;
        this.userService = userService;
        this.accountDeletionService = accountDeletionService;
    }

    // o front chama logo apos o login: id proprio (pra saber quais mensagens sao "minhas"), foto e o codigo do motorista
    @GetMapping("/me")
    public ResponseEntity<UserMeResponseDTO> me(Authentication authentication) {
        return ResponseEntity.ok(userService.me(AuthenticatedUser.id(authentication)));
    }

    @PostMapping("/send-code-updateUser")
    public ResponseEntity<Void>  sendCodeToUser(Authentication authentication){
     userService.sendCodeToUser(AuthenticatedUser.id(authentication));
     return ResponseEntity.ok().build();
    }

    @PutMapping("/update-user")
    public ResponseEntity<User> updateUser(@RequestBody UpdateUser updateUser, Authentication authentication){
        userService.updateUser(updateUser, AuthenticatedUser.id(authentication));
        return ResponseEntity.ok().build();
    }

    // 1) pega a URL de upload da foto  2) sobe a imagem direto no MinIO  3) manda o fileKey no update-photo
    @GetMapping("/photo-upload-url")
    public ResponseEntity<StorageService.PresignedUrlResponse> photoUploadUrl(@RequestParam String fileName,
                                                                              @RequestParam String contentType,
                                                                              Authentication authentication) {
        return ResponseEntity.ok(userService.photoUploadUrl(AuthenticatedUser.id(authentication), fileName, contentType));
    }

    @PutMapping("/update-photo")
    public ResponseEntity<Map<String, String>> updatePhoto(@RequestBody @Valid UpdatePhotoRequestDTO request,
                                                           Authentication authentication) {
        User user = userService.updatePhoto(AuthenticatedUser.id(authentication), request.fileKey());
        return ResponseEntity.ok(Map.of("photoKey", user.getPhotoKey()));
    }

    //nao sei se vai usar isso so pra teste
    // exportar meus dados (LGPD): tudo que o app guarda da pessoa, em JSON. 429 se pedir de novo antes de 5 minutos
    @GetMapping("/me/export")
    public ResponseEntity<java.util.Map<String, Object>> exportMyData(Authentication authentication) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"meus-dados-vanroute.json\"")
                .body(dataExportService.export(AuthenticatedUser.id(authentication)));
    }

    // excluir a propria conta: { "password": "..." }. 204; 400 senha errada; 409 com contrato ativo,
    // proposta pendente ou parcela atrasada. Os dados pessoais somem e a conta vira "Usuario removido"
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@RequestBody(required = false) java.util.Map<String, String> body,
                                                Authentication authentication) {
        accountDeletionService.deleteMyAccount(AuthenticatedUser.id(authentication), body == null ? null : body.get("password"));
        return ResponseEntity.noContent().build();
    }
}

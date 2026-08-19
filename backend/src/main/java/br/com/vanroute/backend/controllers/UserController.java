package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.user.ForgotPasswordRequest;
import br.com.vanroute.backend.dtos.user.UpdateUser;
import br.com.vanroute.backend.models.user.User;//sem DTO por enquanto
import br.com.vanroute.backend.services.UserService;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{cpf}")
    @Valid
    public ResponseEntity<User> getUserById(@PathVariable String cpf) {
        return userService.findByCpf(cpf)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/send-code-updateUser")
    public ResponseEntity<Void>  sendCodeToUser(Authentication authentication){
     String cpf = authentication.getName();
     userService.sendCodeToUser(cpf);
     return ResponseEntity.ok().build();
    }

    @PutMapping("/update-user")
    public ResponseEntity<User> updateUser(@RequestBody UpdateUser updateUser, Authentication authentication){
        String cpf = authentication.getName();
        userService.updateUser(updateUser, cpf);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/forgot-password")
    public String forgetPassword(@RequestBody ForgotPasswordRequest forgotPasswordRequest, Authentication authentication) {
        String cpf = authentication.getName();
        return userService.forgetPassword(cpf, forgotPasswordRequest);
    }

        //nao sei se vai usar isso so pra teste
    // deixa o deleteUser ser feliz
    @DeleteMapping("/{id}/{code}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id, @PathVariable String code) {
        userService.deleteById(id, code);
        return ResponseEntity.noContent().build();
    }
}

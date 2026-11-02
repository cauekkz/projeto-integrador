package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.group.DriverGroupRequestDTO;
import br.com.vanroute.backend.dtos.group.DriverGroupResponseDTO;
import br.com.vanroute.backend.dtos.group.GroupNoticeRequestDTO;
import br.com.vanroute.backend.dtos.group.GroupNoticeResponseDTO;
import br.com.vanroute.backend.services.DriverGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// grupos de alunos do motorista
@RestController
@RequestMapping("/api/driver/groups")
public class DriverGroupController {

    private final DriverGroupService driverGroupService;

    public DriverGroupController(DriverGroupService driverGroupService) {
        this.driverGroupService = driverGroupService;
    }

    @GetMapping
    public List<DriverGroupResponseDTO> list(Authentication authentication) {
        return driverGroupService.list(AuthenticatedUser.id(authentication));
    }

    @PostMapping
    public ResponseEntity<DriverGroupResponseDTO> create(@RequestBody @Valid DriverGroupRequestDTO request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driverGroupService.create(request, AuthenticatedUser.id(authentication)));
    }

    @PutMapping("/{groupId}")
    public DriverGroupResponseDTO rename(@PathVariable UUID groupId, @RequestBody @Valid DriverGroupRequestDTO request,
                                         Authentication authentication) {
        return driverGroupService.rename(groupId, request, AuthenticatedUser.id(authentication));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(@PathVariable UUID groupId, Authentication authentication) {
        driverGroupService.delete(groupId, AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/students/{studentId}")
    public DriverGroupResponseDTO addStudent(@PathVariable UUID groupId, @PathVariable UUID studentId, Authentication authentication) {
        return driverGroupService.addStudent(groupId, studentId, AuthenticatedUser.id(authentication));
    }

    @DeleteMapping("/{groupId}/students/{studentId}")
    public DriverGroupResponseDTO removeStudent(@PathVariable UUID groupId, @PathVariable UUID studentId, Authentication authentication) {
        return driverGroupService.removeStudent(groupId, studentId, AuthenticatedUser.id(authentication));
    }

    // "nao vou buscar hoje" pra todos os pais do grupo; motivo opcional
    @PostMapping("/{groupId}/notice")
    public GroupNoticeResponseDTO notice(@PathVariable UUID groupId, @RequestBody(required = false) @Valid GroupNoticeRequestDTO request,
                                         Authentication authentication) {
        return driverGroupService.notice(groupId, request == null ? null : request.reason(), AuthenticatedUser.id(authentication));
    }
}

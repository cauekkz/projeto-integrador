package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.user.UpdateUser;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserController userController;

    private User user;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .build();

        user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Mbappé");
        user.setEmail("mbappe@teste.com");
        user.setCpf("12345678901");
    }

    // =========================================================
    // GET /api/users/{id}
    // =========================================================

    @Test
    void shouldReturnUserWhenUserExists() throws Exception {

        UUID id = user.getId();

        when(userService.findById(id))
                .thenReturn(Optional.of(user));

        mockMvc.perform(
                        get("/api/users/{id}", id)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Mbappé"))
                .andExpect(jsonPath("$.email").value("mbappe@teste.com"))
                .andExpect(jsonPath("$.cpf").value("12345678901"));

        verify(userService)
                .findById(id);
    }

    @Test
    void shouldReturn404WhenUserDoesNotExist() throws Exception {

        UUID id = UUID.randomUUID();

        when(userService.findById(id))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/api/users/{id}", id)
                )
                .andExpect(status().isNotFound());

        verify(userService)
                .findById(id);
    }

    // =========================================================
    // POST /api/users/send-code-updateUser
    // =========================================================

    @Test
    void shouldSendCodeToUserSuccessfully() throws Exception {

        when(authentication.getName())
                .thenReturn("12345678901");

        doNothing()
                .when(userService)
                .sendCodeToUser("12345678901");

        mockMvc.perform(
                        post("/api/users/send-code-updateUser")
                                .principal(authentication)
                )
                .andExpect(status().isOk());

        verify(authentication)
                .getName();

        verify(userService)
                .sendCodeToUser("12345678901");
    }

    // =========================================================
    // PUT /api/users/update-user
    // =========================================================

    @Test
    void shouldUpdateUserSuccessfully() throws Exception {

        when(authentication.getName())
                .thenReturn("12345678901");

        when(userService.updateUser(
                any(UpdateUser.class),
                eq("12345678901")
        )).thenReturn("User updated successfully");

        mockMvc.perform(
                        put("/api/users/update-user")
                                .principal(authentication)
                                .contentType("application/json")
                                .content("""
                                    {
                                        "email": "novoemail@teste.com",
                                        "phone": "11888888888",
                                        "code": "123456"
                                    }
                                """)
                )
                .andExpect(status().isOk());

        verify(authentication)
                .getName();

        verify(userService)
                .updateUser(
                        any(UpdateUser.class),
                        eq("12345678901")
                );
    }

    // =========================================================
    // DELETE /api/users/{id}/{code}
    // =========================================================

    @Test
    void shouldDeleteUserSuccessfully() throws Exception {

        UUID id = UUID.randomUUID();
        String code = "123456";

        doNothing()
                .when(userService)
                .deleteById(id, code);

        mockMvc.perform(
                        delete("/api/users/{id}/{code}", id, code)
                )
                .andExpect(status().isNoContent());

        verify(userService)
                .deleteById(id, code);
    }
}
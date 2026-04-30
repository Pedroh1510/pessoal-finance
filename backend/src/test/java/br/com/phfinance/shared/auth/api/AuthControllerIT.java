package br.com.phfinance.shared.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.http.Cookie;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "management.health.rabbit.enabled=false",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean
    JavaMailSender mailSender;

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL    = "/api/auth/login";
    private static final String ME_URL       = "/api/auth/me";
    private static final String LOGOUT_URL   = "/api/auth/logout";
    private static final String FORGOT_URL   = "/api/auth/forgot-password";
    private static final String RESET_URL    = "/api/auth/reset-password";

    private String body(Map<String, String> map) throws Exception {
        return objectMapper.writeValueAsString(map);
    }

    @Test
    void register_withValidData_returns201WithUserFields() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "newuser@test.com", "name", "New User", "password", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@test.com"))
                .andExpect(jsonPath("$.name").value("New User"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        String email = "dup@test.com";
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "name", "Dup", "password", "password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "name", "Dup2", "password", "password123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void login_withValidCredentials_returns200AndSetsCookie() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "login@test.com", "name", "Login User", "password", "password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "login@test.com", "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@test.com"))
                .andExpect(cookie().exists("SESSION"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "wrong@test.com", "name", "Wrong", "password", "password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "wrong@test.com", "password", "badpassword"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withActiveSession_returns200() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "me@test.com", "name", "Me User", "password", "password123"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "me@test.com", "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        mockMvc.perform(get(ME_URL).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@test.com"));
    }

    @Test
    void logout_invalidatesSession() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "logout@test.com", "name", "Logout", "password", "password123"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "logout@test.com", "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");

        mockMvc.perform(post(LOGOUT_URL).cookie(sessionCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ME_URL).cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_returns204_regardlessOfEmailExistence() throws Exception {
        mockMvc.perform(post(FORGOT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "nonexistent@test.com"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "existing@test.com", "name", "Existing", "password", "password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(FORGOT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "existing@test.com"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void resetPassword_withInvalidToken_returns400() throws Exception {
        mockMvc.perform(post(RESET_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", "nonexistent-token", "newPassword", "newpassword123"))))
                .andExpect(status().isBadRequest());
    }
}

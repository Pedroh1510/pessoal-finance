package br.com.phfinance.shared.auth.application;

import br.com.phfinance.shared.auth.api.*;
import br.com.phfinance.shared.auth.domain.*;
import br.com.phfinance.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock EmailService emailService;
    @Mock AuthenticationManager authenticationManager;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, tokenRepository, passwordEncoder, emailService, authenticationManager);
    }

    // ---------- register ----------

    @Test
    void register_withNewEmail_returnsAuthResponse() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(
                new RegisterRequest("new@test.com", "New User", "password123"));

        assertThat(response.email()).isEqualTo("new@test.com");
        assertThat(response.name()).isEqualTo("New User");
    }

    @Test
    void register_withDuplicateEmail_throwsBusinessException() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("dup@test.com", "Dup", "password123")))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- login ----------

    @Test
    void login_withValidCredentials_returnsAuthResponse() {
        User user = User.create("user@test.com", "User", "password123", passwordEncoder);
        Authentication mockAuth = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(mockAuth);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(
                new LoginRequest("user@test.com", "password123"), new MockHttpServletRequest());

        assertThat(response.email()).isEqualTo("user@test.com");
    }

    @Test
    void login_withInvalidCredentials_throws401() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("user@test.com", "wrong"), new MockHttpServletRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);
    }

    // ---------- logout ----------

    @Test
    void logout_invalidatesSession() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);

        authService.logout(request);

        verify(session).invalidate();
    }

    // ---------- me ----------

    @Test
    void me_withAuthenticatedPrincipal_returnsUser() {
        User user = User.create("user@test.com", "User", "password123", passwordEncoder);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        Principal principal = () -> "user@test.com";

        Optional<AuthResponse> result = authService.me(principal);

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo("user@test.com");
    }

    @Test
    void me_withNullPrincipal_returnsEmpty() {
        assertThat(authService.me(null)).isEmpty();
    }

    // ---------- forgotPassword ----------

    @Test
    void forgotPassword_withExistingEmail_savesTokenAndSendsEmail() {
        User user = User.create("user@test.com", "User", "password123", passwordEncoder);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        authService.forgotPassword("user@test.com");

        verify(tokenRepository).deleteByUserId(user.getId());
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("user@test.com"), any());
    }

    @Test
    void forgotPassword_withNonExistingEmail_doesNothing() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        authService.forgotPassword("ghost@test.com");

        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    // ---------- resetPassword ----------

    @Test
    void resetPassword_withValidToken_updatesPassword() {
        User user = User.create("user@test.com", "User", "password123", passwordEncoder);
        PasswordResetToken token = PasswordResetToken.create(user);
        when(tokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.resetPassword(new ResetPasswordRequest(token.getToken(), "newpassword123"));

        assertThat(passwordEncoder.matches("newpassword123", user.getPassword())).isTrue();
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void resetPassword_withNonExistentToken_throws400() {
        when(tokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordRequest("bad-token", "newpassword123")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void resetPassword_withUsedToken_throws400() {
        User user = User.create("user@test.com", "User", "password123", passwordEncoder);
        PasswordResetToken token = PasswordResetToken.create(user);
        token.markUsed();
        when(tokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordRequest(token.getToken(), "newpassword123")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }
}

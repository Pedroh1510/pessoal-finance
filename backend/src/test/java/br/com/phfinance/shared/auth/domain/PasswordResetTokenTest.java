package br.com.phfinance.shared.auth.domain;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class PasswordResetTokenTest {

    private User testUser() {
        return User.create("user@test.com", "Test", "password123", new BCryptPasswordEncoder(4));
    }

    @Test
    void create_setsAllFieldsCorrectly() {
        User user = testUser();
        PasswordResetToken token = PasswordResetToken.create(user);

        assertThat(token.getId()).isNotNull();
        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void isValid_freshToken_returnsTrue() {
        PasswordResetToken token = PasswordResetToken.create(testUser());
        assertThat(token.isValid()).isTrue();
    }

    @Test
    void isValid_usedToken_returnsFalse() {
        PasswordResetToken token = PasswordResetToken.create(testUser());
        token.markUsed();
        assertThat(token.isValid()).isFalse();
    }

    @Test
    void isValid_expiredToken_returnsFalse() throws Exception {
        PasswordResetToken token = PasswordResetToken.create(testUser());
        Field f = PasswordResetToken.class.getDeclaredField("expiresAt");
        f.setAccessible(true);
        f.set(token, LocalDateTime.now().minusMinutes(1));

        assertThat(token.isValid()).isFalse();
    }

    @Test
    void tokenValue_isUniquePerCreate() {
        PasswordResetToken t1 = PasswordResetToken.create(testUser());
        PasswordResetToken t2 = PasswordResetToken.create(testUser());
        assertThat(t1.getToken()).isNotEqualTo(t2.getToken());
    }
}

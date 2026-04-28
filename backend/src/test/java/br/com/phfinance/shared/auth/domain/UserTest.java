package br.com.phfinance.shared.auth.domain;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void create_withValidInputs_setsFieldsAndHashesPassword() {
        User user = User.create("Test@Example.com", "Test User", "password123", encoder);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("test@example.com"); // lowercased
        assertThat(user.getName()).isEqualTo("Test User");
        assertThat(encoder.matches("password123", user.getPassword())).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void create_withInvalidEmail_throws() {
        assertThatThrownBy(() -> User.create("not-an-email", "Test", "password123", encoder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void create_withBlankName_throws() {
        assertThatThrownBy(() -> User.create("user@test.com", "  ", "password123", encoder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void create_withShortPassword_throws() {
        assertThatThrownBy(() -> User.create("user@test.com", "Test", "short", encoder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8");
    }

    @Test
    void changePassword_updatesHashedPasswordAndTimestamp() {
        User user = User.create("user@test.com", "Test", "password123", encoder);
        var oldUpdatedAt = user.getUpdatedAt();

        user.changePassword("newpassword456", encoder);

        assertThat(encoder.matches("newpassword456", user.getPassword())).isTrue();
        assertThat(encoder.matches("password123", user.getPassword())).isFalse();
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(oldUpdatedAt);
    }

    @Test
    void changePassword_withShortPassword_throws() {
        User user = User.create("user@test.com", "Test", "password123", encoder);
        assertThatThrownBy(() -> user.changePassword("short", encoder))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

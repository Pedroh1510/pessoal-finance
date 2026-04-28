package br.com.phfinance.shared.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static User create(String email, String name, String rawPassword, PasswordEncoder encoder) {
        if (email == null || !email.contains("@"))
            throw new IllegalArgumentException("Invalid email");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name is required");
        if (rawPassword == null || rawPassword.length() < 8)
            throw new IllegalArgumentException("Password must be at least 8 characters");

        User u = new User();
        u.id = UUID.randomUUID();
        u.email = email.toLowerCase().trim();
        u.name = name.trim();
        u.password = encoder.encode(rawPassword);
        u.createdAt = LocalDateTime.now();
        u.updatedAt = LocalDateTime.now();
        return u;
    }

    public void changePassword(String newRawPassword, PasswordEncoder encoder) {
        if (newRawPassword == null || newRawPassword.length() < 8)
            throw new IllegalArgumentException("Password must be at least 8 characters");
        this.password = encoder.encode(newRawPassword);
        this.updatedAt = LocalDateTime.now();
    }
}

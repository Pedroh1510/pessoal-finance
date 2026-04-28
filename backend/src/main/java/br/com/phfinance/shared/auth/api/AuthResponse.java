package br.com.phfinance.shared.auth.api;

import br.com.phfinance.shared.auth.domain.User;

import java.util.UUID;

public record AuthResponse(UUID id, String email, String name) {

    public static AuthResponse from(User user) {
        return new AuthResponse(user.getId(), user.getEmail(), user.getName());
    }
}

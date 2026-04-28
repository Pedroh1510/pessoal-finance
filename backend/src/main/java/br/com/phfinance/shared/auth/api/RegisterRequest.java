package br.com.phfinance.shared.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Size(min = 8, max = 100) String password
) {}

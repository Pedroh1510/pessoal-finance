package br.com.phfinance.shared.config;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {

    private final JdbcTemplate jdbcTemplate;

    public AuditorAwareImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        List<UUID> result = jdbcTemplate.query(
                "SELECT id FROM users WHERE email = ? LIMIT 1",
                (ResultSet rs, int row) -> UUID.fromString(rs.getString("id")),
                auth.getName()
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}

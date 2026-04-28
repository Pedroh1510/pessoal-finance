package br.com.phfinance.shared.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Testcontainers
class MigrationV4IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void users_table_exists() {
        assertThatCode(() -> jdbc.queryForObject(
                "SELECT count(*) FROM users", Integer.class))
                .doesNotThrowAnyException();
    }

    @Test
    void password_reset_tokens_table_exists() {
        assertThatCode(() -> jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_tokens", Integer.class))
                .doesNotThrowAnyException();
    }

    @Test
    void spring_session_table_exists() {
        assertThatCode(() -> jdbc.queryForObject(
                "SELECT count(*) FROM SPRING_SESSION", Integer.class))
                .doesNotThrowAnyException();
    }
}

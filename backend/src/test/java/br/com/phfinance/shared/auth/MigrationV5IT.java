package br.com.phfinance.shared.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@Testcontainers
class MigrationV5IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    JavaMailSender mailSender;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void transaction_has_audit_columns() {
        var cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'transaction' AND column_name IN ('created_by','updated_by')",
                String.class);
        assertThat(cols).containsExactlyInAnyOrder("created_by", "updated_by");
    }

    @Test
    void category_has_audit_columns() {
        var cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'category' AND column_name IN ('created_by','updated_by')",
                String.class);
        assertThat(cols).containsExactlyInAnyOrder("created_by", "updated_by");
    }
}

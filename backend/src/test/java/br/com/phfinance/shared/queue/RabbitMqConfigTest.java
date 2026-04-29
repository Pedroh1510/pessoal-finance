package br.com.phfinance.shared.queue;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@Testcontainers
class RabbitMqConfigTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @MockBean JavaMailSender mailSender;

    @Autowired AmqpAdmin amqpAdmin;

    @Autowired @Qualifier("financeUploadQueue")    Queue financeUploadQueue;
    @Autowired @Qualifier("inflationUploadQueue")  Queue inflationUploadQueue;
    @Autowired @Qualifier("financeReprocessQueue") Queue financeReprocessQueue;

    @Test
    void financeUploadQueueDeclared() {
        QueueInformation info = amqpAdmin.getQueueInfo(RabbitMqConfig.FINANCE_UPLOAD_QUEUE);
        assertThat(info).isNotNull();
    }

    @Test
    void inflationUploadQueueDeclared() {
        assertThat(amqpAdmin.getQueueInfo(RabbitMqConfig.INFLATION_UPLOAD_QUEUE)).isNotNull();
    }

    @Test
    void financeReprocessQueueDeclared() {
        assertThat(amqpAdmin.getQueueInfo(RabbitMqConfig.FINANCE_REPROCESS_QUEUE)).isNotNull();
    }

    @Test
    void dlqsDeclared() {
        assertThat(amqpAdmin.getQueueInfo(RabbitMqConfig.FINANCE_UPLOAD_DLQ)).isNotNull();
        assertThat(amqpAdmin.getQueueInfo(RabbitMqConfig.INFLATION_UPLOAD_DLQ)).isNotNull();
        assertThat(amqpAdmin.getQueueInfo(RabbitMqConfig.FINANCE_REPROCESS_DLQ)).isNotNull();
    }

    @Test
    void financeUploadQueue_hasDlxWired() {
        assertThat(financeUploadQueue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMqConfig.DLX_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMqConfig.FINANCE_UPLOAD_DLQ);
    }

    @Test
    void inflationUploadQueue_hasDlxWired() {
        assertThat(inflationUploadQueue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMqConfig.DLX_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMqConfig.INFLATION_UPLOAD_DLQ);
    }

    @Test
    void financeReprocessQueue_hasDlxWired() {
        assertThat(financeReprocessQueue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMqConfig.DLX_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMqConfig.FINANCE_REPROCESS_DLQ);
    }
}

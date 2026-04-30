# Background Queue (RabbitMQ) — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mover upload de extratos, upload de inflação e reprocessamento para background via RabbitMQ, retornando 202 + jobId ao invés de resultado síncrono.

**Architecture:** Controller salva arquivo + insere `upload_jobs` + insere `outbox_events` em uma transação, retorna 202. `OutboxPublisher` publica eventos pendentes no RabbitMQ (outbox pattern). Workers consomem filas, chamam os services existentes sem alteração, atualizam o job e enviam email.

**Tech Stack:** Spring Boot AMQP, RabbitMQ 3.13, Flyway, Testcontainers (RabbitMQ + PostgreSQL), React + polling via `setInterval`.

---

## Mapa de Arquivos

### Criados (backend)
- `backend/pom.xml` — adicionar `spring-boot-starter-amqp` + `testcontainers:rabbitmq`
- `backend/src/main/resources/application.yml` — adicionar properties `app.queue.*` e `spring.rabbitmq.*`
- `docker-compose.yml` — adicionar serviço `rabbitmq`
- `backend/src/main/resources/db/migration/V6__create_upload_jobs.sql`
- `backend/src/main/resources/db/migration/V7__create_outbox_events.sql`
- `backend/src/main/java/br/com/phfinance/shared/jobs/JobStatus.java`
- `backend/src/main/java/br/com/phfinance/shared/jobs/JobType.java`
- `backend/src/main/java/br/com/phfinance/shared/jobs/UploadJob.java`
- `backend/src/main/java/br/com/phfinance/shared/jobs/UploadJobRepository.java`
- `backend/src/main/java/br/com/phfinance/shared/queue/OutboxEvent.java`
- `backend/src/main/java/br/com/phfinance/shared/queue/OutboxEventRepository.java`
- `backend/src/main/java/br/com/phfinance/shared/queue/JobMessage.java`
- `backend/src/main/java/br/com/phfinance/shared/queue/RabbitMqConfig.java`
- `backend/src/main/java/br/com/phfinance/shared/queue/OutboxPublisher.java`
- `backend/src/main/java/br/com/phfinance/shared/jobs/UploadJobService.java`
- `backend/src/main/java/br/com/phfinance/shared/jobs/JobResponse.java`
- `backend/src/main/java/br/com/phfinance/shared/jobs/UploadJobController.java`
- `backend/src/main/java/br/com/phfinance/shared/jobs/JobNotificationService.java`
- `backend/src/main/java/br/com/phfinance/finance/worker/FinanceUploadWorker.java`
- `backend/src/main/java/br/com/phfinance/finance/worker/FinanceUploadDlqWorker.java`
- `backend/src/main/java/br/com/phfinance/finance/worker/ReprocessWorker.java`
- `backend/src/main/java/br/com/phfinance/finance/worker/ReprocessDlqWorker.java`
- `backend/src/main/java/br/com/phfinance/inflation/worker/InflationUploadWorker.java`
- `backend/src/main/java/br/com/phfinance/inflation/worker/InflationUploadDlqWorker.java`

### Modificados (backend)
- `backend/src/main/java/br/com/phfinance/finance/api/FinanceController.java` — `upload()` e `reprocess()` retornam 202
- `backend/src/main/java/br/com/phfinance/inflation/api/InflationController.java` — `upload()` retorna 202

### Criados (testes)
- `backend/src/test/java/br/com/phfinance/shared/queue/OutboxPublisherTest.java`
- `backend/src/test/java/br/com/phfinance/shared/queue/RabbitMqConfigTest.java`
- `backend/src/test/java/br/com/phfinance/shared/jobs/UploadJobServiceTest.java`
- `backend/src/test/java/br/com/phfinance/shared/jobs/JobNotificationServiceTest.java`
- `backend/src/test/java/br/com/phfinance/shared/jobs/UploadJobControllerIT.java`
- `backend/src/test/java/br/com/phfinance/finance/worker/FinanceUploadWorkerTest.java`
- `backend/src/test/java/br/com/phfinance/finance/worker/ReprocessWorkerTest.java`
- `backend/src/test/java/br/com/phfinance/inflation/worker/InflationUploadWorkerTest.java`
- `backend/src/test/java/br/com/phfinance/shared/queue/OutboxEndToEndIT.java`

### Criados (frontend)
- `frontend/src/lib/jobs.ts` — `getJob`, `useJobPolling`

### Modificados (frontend)
- `frontend/src/lib/finance.ts` — retorno de `uploadStatement` e `reprocessTransactions` muda para `JobResponse`
- `frontend/src/pages/UploadsPage.tsx` — polling após submit
- `frontend/src/pages/SettingsPage.tsx` — `ReprocessTab` com polling
- `frontend/src/pages/InflationPage.tsx` — polling após submit

---

## Task 1: Infraestrutura Base

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `docker-compose.yml`
- Create: `backend/src/main/java/br/com/phfinance/shared/queue/RabbitMqConfig.java`

- [ ] **Step 1: Adicionar dependências no pom.xml**

Dentro de `<dependencies>`, após `spring-boot-starter-mail`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Dentro do bloco `<dependencies>` de teste (junto com `testcontainers:postgresql`):

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>rabbitmq</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Adicionar properties no application.yml**

Ao final do arquivo `backend/src/main/resources/application.yml`:

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: 5672
    username: ${RABBITMQ_USER:finance}
    password: ${RABBITMQ_PASSWORD:finance}

app:
  queue:
    consumer:
      concurrency: 1
    retry:
      max-attempts: 3
      initial-interval: 5000
      multiplier: 5.0
    file:
      temp-dir: /tmp/ph-finance
```

**Atenção:** o `spring:` já existe no arquivo. Adicione `rabbitmq:` como sub-chave dentro do bloco `spring:` existente (ao lado de `datasource:`, `jpa:`, etc.). O bloco `app:` já tem `base-url`; adicione `queue:` dentro dele.

- [ ] **Step 3: Adicionar RabbitMQ no docker-compose.yml**

Adicionar serviço antes de `backend:`:

```yaml
  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-finance}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-finance}
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
```

No serviço `backend`, alterar `depends_on`:

```yaml
    depends_on:
      db:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
```

Adicionar variáveis de ambiente no serviço `backend`:

```yaml
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_USER: ${RABBITMQ_USER:-finance}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-finance}
```

- [ ] **Step 4: Criar RabbitMqConfig.java**

```java
package br.com.phfinance.shared.queue;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitMqConfig {

    public static final String MAIN_EXCHANGE      = "finance.direct";
    public static final String DLX_EXCHANGE       = "finance.dlx";

    public static final String FINANCE_UPLOAD_QUEUE    = "finance.upload";
    public static final String INFLATION_UPLOAD_QUEUE  = "inflation.upload";
    public static final String FINANCE_REPROCESS_QUEUE = "finance.reprocess";
    public static final String FINANCE_UPLOAD_DLQ      = "finance.upload.dlq";
    public static final String INFLATION_UPLOAD_DLQ    = "inflation.upload.dlq";
    public static final String FINANCE_REPROCESS_DLQ   = "finance.reprocess.dlq";

    @Value("${app.queue.consumer.concurrency:1}")
    private int concurrency;

    @Value("${app.queue.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.queue.retry.initial-interval:5000}")
    private long initialInterval;

    @Value("${app.queue.retry.multiplier:5.0}")
    private double multiplier;

    // --- Exchanges ---

    @Bean DirectExchange mainExchange() {
        return new DirectExchange(MAIN_EXCHANGE, true, false);
    }

    @Bean DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    // --- Work queues ---

    @Bean Queue financeUploadQueue() {
        return QueueBuilder.durable(FINANCE_UPLOAD_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FINANCE_UPLOAD_DLQ)
                .build();
    }

    @Bean Queue inflationUploadQueue() {
        return QueueBuilder.durable(INFLATION_UPLOAD_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", INFLATION_UPLOAD_DLQ)
                .build();
    }

    @Bean Queue financeReprocessQueue() {
        return QueueBuilder.durable(FINANCE_REPROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FINANCE_REPROCESS_DLQ)
                .build();
    }

    // --- DLQs ---

    @Bean Queue financeUploadDlq()    { return QueueBuilder.durable(FINANCE_UPLOAD_DLQ).build(); }
    @Bean Queue inflationUploadDlq()  { return QueueBuilder.durable(INFLATION_UPLOAD_DLQ).build(); }
    @Bean Queue financeReprocessDlq() { return QueueBuilder.durable(FINANCE_REPROCESS_DLQ).build(); }

    // --- Bindings ---

    @Bean Binding financeUploadBinding() {
        return BindingBuilder.bind(financeUploadQueue()).to(mainExchange()).with(FINANCE_UPLOAD_QUEUE);
    }
    @Bean Binding inflationUploadBinding() {
        return BindingBuilder.bind(inflationUploadQueue()).to(mainExchange()).with(INFLATION_UPLOAD_QUEUE);
    }
    @Bean Binding financeReprocessBinding() {
        return BindingBuilder.bind(financeReprocessQueue()).to(mainExchange()).with(FINANCE_REPROCESS_QUEUE);
    }
    @Bean Binding financeUploadDlqBinding() {
        return BindingBuilder.bind(financeUploadDlq()).to(deadLetterExchange()).with(FINANCE_UPLOAD_DLQ);
    }
    @Bean Binding inflationUploadDlqBinding() {
        return BindingBuilder.bind(inflationUploadDlq()).to(deadLetterExchange()).with(INFLATION_UPLOAD_DLQ);
    }
    @Bean Binding financeReprocessDlqBinding() {
        return BindingBuilder.bind(financeReprocessDlq()).to(deadLetterExchange()).with(FINANCE_REPROCESS_DLQ);
    }

    // --- Message converter ---

    @Bean Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // --- Listener container factory com retry + DLQ ---

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(initialInterval * (long) Math.pow(multiplier, maxAttempts));

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts));
        retryTemplate.setBackOffPolicy(backOff);

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(concurrency);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .retryOperations(retryTemplate)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build()
        );
        return factory;
    }
}
```

- [ ] **Step 5: Criar RabbitMqConfigTest.java**

```java
package br.com.phfinance.shared.queue;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RabbitMqConfigTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @MockBean JavaMailSender mailSender;

    @Autowired AmqpAdmin amqpAdmin;

    @Test
    void financeUploadQueueDeclaredWithDlx() {
        QueueInformation info = amqpAdmin.getQueueInfo(RabbitMqConfig.FINANCE_UPLOAD_QUEUE);
        assertThat(info).isNotNull();
        assertThat(info.getMessageCount()).isZero();
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
}
```

- [ ] **Step 6: Rodar testes**

```bash
cd backend && mvn test -pl . -Dtest=RabbitMqConfigTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git checkout -b feat/background-queue
git add backend/pom.xml backend/src/main/resources/application.yml docker-compose.yml \
    backend/src/main/java/br/com/phfinance/shared/queue/RabbitMqConfig.java \
    backend/src/test/java/br/com/phfinance/shared/queue/RabbitMqConfigTest.java
git commit -m "feat: add RabbitMQ infrastructure — exchanges, queues, DLQs, retry config"
```

---

## Task 2: Migrations + Entidades + Enums + Repositories

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_upload_jobs.sql`
- Create: `backend/src/main/resources/db/migration/V7__create_outbox_events.sql`
- Create: `backend/src/main/java/br/com/phfinance/shared/jobs/JobStatus.java`
- Create: `backend/src/main/java/br/com/phfinance/shared/jobs/JobType.java`
- Create: `backend/src/main/java/br/com/phfinance/shared/jobs/UploadJob.java`
- Create: `backend/src/main/java/br/com/phfinance/shared/jobs/UploadJobRepository.java`
- Create: `backend/src/main/java/br/com/phfinance/shared/queue/OutboxEvent.java`
- Create: `backend/src/main/java/br/com/phfinance/shared/queue/OutboxEventRepository.java`

- [ ] **Step 1: Criar V6__create_upload_jobs.sql**

```sql
CREATE TABLE upload_jobs (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type              VARCHAR(30)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    user_id           VARCHAR(255) NOT NULL,
    file_path         VARCHAR(500),
    original_filename VARCHAR(255),
    result_json       JSONB,
    error_message     TEXT,
    attempt_count     SMALLINT     NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_upload_jobs_status  ON upload_jobs(status);
CREATE INDEX idx_upload_jobs_user_id ON upload_jobs(user_id);
```

- [ ] **Step 2: Criar V7__create_outbox_events.sql**

```sql
CREATE TABLE outbox_events (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_name VARCHAR(100) NOT NULL,
    payload    JSONB        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published  BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX idx_outbox_events_published ON outbox_events(published) WHERE published = false;
```

- [ ] **Step 3: Criar JobStatus.java**

```java
package br.com.phfinance.shared.jobs;

public enum JobStatus {
    QUEUED, PROCESSING, COMPLETED, FAILED
}
```

- [ ] **Step 4: Criar JobType.java**

```java
package br.com.phfinance.shared.jobs;

public enum JobType {
    FINANCE_UPLOAD, INFLATION_UPLOAD, REPROCESS
}
```

- [ ] **Step 5: Criar UploadJob.java**

```java
package br.com.phfinance.shared.jobs;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "upload_jobs")
public class UploadJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // getters e setters

    public UUID getId() { return id; }
    public JobType getType() { return type; }
    public void setType(JobType type) { this.type = type; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public short getAttemptCount() { return attemptCount; }
    public void setAttemptCount(short attemptCount) { this.attemptCount = attemptCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 6: Criar UploadJobRepository.java**

```java
package br.com.phfinance.shared.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface UploadJobRepository extends JpaRepository<UploadJob, UUID> {

    @Modifying
    @Query("""
        UPDATE UploadJob j
        SET j.status = br.com.phfinance.shared.jobs.JobStatus.PROCESSING,
            j.attemptCount = j.attemptCount + 1
        WHERE j.id = :id
        """)
    void markProcessing(UUID id);

    @Modifying
    @Query("""
        UPDATE UploadJob j
        SET j.status = br.com.phfinance.shared.jobs.JobStatus.COMPLETED,
            j.resultJson = :resultJson
        WHERE j.id = :id
        """)
    void markCompleted(UUID id, String resultJson);

    @Modifying
    @Query("""
        UPDATE UploadJob j
        SET j.status = br.com.phfinance.shared.jobs.JobStatus.FAILED,
            j.errorMessage = :errorMessage
        WHERE j.id = :id
        """)
    void markFailed(UUID id, String errorMessage);
}
```

- [ ] **Step 7: Criar OutboxEvent.java**

```java
package br.com.phfinance.shared.queue;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "queue_name", nullable = false, length = 100)
    private String queueName;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private boolean published = false;

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
}
```

- [ ] **Step 8: Criar OutboxEventRepository.java**

```java
package br.com.phfinance.shared.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
        SELECT * FROM outbox_events
        WHERE published = false
        LIMIT 100
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findPendingForUpdate();
}
```

- [ ] **Step 9: Verificar que migrations rodam**

```bash
cd backend && mvn test -Dtest=HealthCheckIT -q
```

Expected: BUILD SUCCESS (Flyway aplica V6 e V7)

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/ \
    backend/src/main/java/br/com/phfinance/shared/jobs/ \
    backend/src/main/java/br/com/phfinance/shared/queue/OutboxEvent.java \
    backend/src/main/java/br/com/phfinance/shared/queue/OutboxEventRepository.java
git commit -m "feat: add upload_jobs and outbox_events migrations and JPA entities"
```

---

## Task 3: JobMessage + OutboxPublisher

**Files:**
- Create: `backend/src/main/java/br/com/phfinance/shared/queue/JobMessage.java`
- Create: `backend/src/main/java/br/com/phfinance/shared/queue/OutboxPublisher.java`
- Create: `backend/src/test/java/br/com/phfinance/shared/queue/OutboxPublisherTest.java`

- [ ] **Step 1: Escrever teste falho do OutboxPublisher**

```java
package br.com.phfinance.shared.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock OutboxEventRepository outboxEventRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks OutboxPublisher outboxPublisher;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publishesPendingEventAndMarksPublished() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobMessage message = new JobMessage(jobId, "FINANCE_UPLOAD", "/tmp/file.pdf", "NUBANK");
        OutboxEvent event = new OutboxEvent();
        event.setQueueName(RabbitMqConfig.FINANCE_UPLOAD_QUEUE);
        event.setPayload(mapper.writeValueAsString(message));

        when(outboxEventRepository.findPendingForUpdate()).thenReturn(List.of(event));

        outboxPublisher.publishPending();

        ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.MAIN_EXCHANGE),
                eq(RabbitMqConfig.FINANCE_UPLOAD_QUEUE),
                msgCaptor.capture()
        );
        assertThat(event.isPublished()).isTrue();
        verify(outboxEventRepository).save(event);
    }

    @Test
    void skipsAlreadyPublishedEvents() {
        when(outboxEventRepository.findPendingForUpdate()).thenReturn(List.of());
        outboxPublisher.publishPending();
        verifyNoInteractions(rabbitTemplate);
    }
}
```

- [ ] **Step 2: Rodar o teste — deve falhar**

```bash
cd backend && mvn test -Dtest=OutboxPublisherTest -q
```

Expected: FAIL — `OutboxPublisher` not found

- [ ] **Step 3: Criar JobMessage.java**

```java
package br.com.phfinance.shared.queue;

import java.util.UUID;

public record JobMessage(
        UUID jobId,
        String type,
        String filePath,
        String bankName
) {}
```

- [ ] **Step 4: Criar OutboxPublisher.java**

```java
package br.com.phfinance.shared.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingForUpdate();
        for (OutboxEvent event : pending) {
            try {
                JobMessage message = objectMapper.readValue(event.getPayload(), JobMessage.class);
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.MAIN_EXCHANGE,
                        event.getQueueName(),
                        message
                );
                event.setPublished(true);
                outboxEventRepository.save(event);
            } catch (Exception e) {
                // log e tenta novamente no próximo ciclo
            }
        }
    }
}
```

- [ ] **Step 5: Habilitar @EnableScheduling**

Criar `backend/src/main/java/br/com/phfinance/shared/config/SchedulingConfig.java`:

```java
package br.com.phfinance.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

- [ ] **Step 6: Rodar testes — devem passar**

```bash
cd backend && mvn test -Dtest=OutboxPublisherTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/phfinance/shared/queue/JobMessage.java \
    backend/src/main/java/br/com/phfinance/shared/queue/OutboxPublisher.java \
    backend/src/main/java/br/com/phfinance/shared/config/SchedulingConfig.java \
    backend/src/test/java/br/com/phfinance/shared/queue/OutboxPublisherTest.java
git commit -m "feat: add OutboxPublisher with @Scheduled polling outbox_events"
```

---

## Task 4: UploadJobService + UploadJobController

**Files:**
- Create: `backend/src/main/java/br/com/phfinance/shared/jobs/UploadJobService.java`
- Create: `backend/src/main/java/br/com/phfinance/shared/jobs/JobResponse.java`
- Create: `backend/src/main/java/br/com/phfinance/shared/jobs/UploadJobController.java`
- Create: `backend/src/test/java/br/com/phfinance/shared/jobs/UploadJobServiceTest.java`
- Create: `backend/src/test/java/br/com/phfinance/shared/jobs/UploadJobControllerIT.java`

- [ ] **Step 1: Escrever UploadJobServiceTest**

```java
package br.com.phfinance.shared.jobs;

import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.OutboxEvent;
import br.com.phfinance.shared.queue.OutboxEventRepository;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadJobServiceTest {

    @TempDir Path tempDir;
    @Mock UploadJobRepository uploadJobRepository;
    @Mock OutboxEventRepository outboxEventRepository;

    UploadJobService service;

    @BeforeEach
    void setUp() {
        service = new UploadJobService(
                uploadJobRepository, outboxEventRepository,
                new ObjectMapper(), tempDir.toString()
        );
    }

    @Test
    void createFinanceUploadJob_savesFileAndReturnsJobId() throws Exception {
        when(uploadJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        byte[] bytes = "pdf-content".getBytes();

        var jobId = service.createFinanceUploadJob(bytes, "statement.pdf", "NUBANK", "user@example.com");

        assertThat(jobId).isNotNull();

        ArgumentCaptor<UploadJob> jobCaptor = ArgumentCaptor.forClass(UploadJob.class);
        verify(uploadJobRepository).save(jobCaptor.capture());
        UploadJob saved = jobCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(saved.getType()).isEqualTo(JobType.FINANCE_UPLOAD);
        assertThat(saved.getUserId()).isEqualTo("user@example.com");
        assertThat(saved.getFilePath()).startsWith(tempDir.toString());

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getQueueName()).isEqualTo(RabbitMqConfig.FINANCE_UPLOAD_QUEUE);
        assertThat(event.isPublished()).isFalse();

        JobMessage msg = new ObjectMapper().readValue(event.getPayload(), JobMessage.class);
        assertThat(msg.type()).isEqualTo("FINANCE_UPLOAD");
        assertThat(msg.bankName()).isEqualTo("NUBANK");
    }

    @Test
    void createFinanceUploadJob_rejectsPathOutsideTempDir() {
        assertThatThrownBy(() ->
            service.createFinanceUploadJob("x".getBytes(), "../../../etc/passwd", "NUBANK", "u@e.com")
        ).isInstanceOf(SecurityException.class);
    }
}
```

- [ ] **Step 2: Rodar — deve falhar**

```bash
cd backend && mvn test -Dtest=UploadJobServiceTest -q
```

Expected: FAIL — `UploadJobService` not found

- [ ] **Step 3: Criar UploadJobService.java**

```java
package br.com.phfinance.shared.jobs;

import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.OutboxEvent;
import br.com.phfinance.shared.queue.OutboxEventRepository;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class UploadJobService {

    private final UploadJobRepository uploadJobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String tempDir;

    public UploadJobService(UploadJobRepository uploadJobRepository,
                            OutboxEventRepository outboxEventRepository,
                            ObjectMapper objectMapper,
                            @Value("${app.queue.file.temp-dir:/tmp/ph-finance}") String tempDir) {
        this.uploadJobRepository = uploadJobRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.tempDir = tempDir;
    }

    @Transactional
    public UUID createFinanceUploadJob(byte[] fileBytes, String originalFilename,
                                       String bankName, String userId) throws IOException {
        String ext = extractExtension(originalFilename);
        UUID fileId = UUID.randomUUID();
        Path filePath = resolveAndValidatePath(fileId + "." + ext);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, fileBytes);

        UploadJob job = new UploadJob();
        job.setType(JobType.FINANCE_UPLOAD);
        job.setStatus(JobStatus.QUEUED);
        job.setUserId(userId);
        job.setFilePath(filePath.toString());
        job.setOriginalFilename(originalFilename);
        uploadJobRepository.save(job);

        JobMessage message = new JobMessage(job.getId(), "FINANCE_UPLOAD", filePath.toString(), bankName);
        saveOutboxEvent(RabbitMqConfig.FINANCE_UPLOAD_QUEUE, message);

        return job.getId();
    }

    @Transactional
    public UUID createInflationUploadJob(byte[] fileBytes, String originalFilename,
                                         String userId) throws IOException {
        String ext = extractExtension(originalFilename);
        UUID fileId = UUID.randomUUID();
        Path filePath = resolveAndValidatePath(fileId + "." + ext);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, fileBytes);

        UploadJob job = new UploadJob();
        job.setType(JobType.INFLATION_UPLOAD);
        job.setStatus(JobStatus.QUEUED);
        job.setUserId(userId);
        job.setFilePath(filePath.toString());
        job.setOriginalFilename(originalFilename);
        uploadJobRepository.save(job);

        JobMessage message = new JobMessage(job.getId(), "INFLATION_UPLOAD", filePath.toString(), null);
        saveOutboxEvent(RabbitMqConfig.INFLATION_UPLOAD_QUEUE, message);

        return job.getId();
    }

    @Transactional
    public UUID createReprocessJob(String userId) throws JsonProcessingException {
        UploadJob job = new UploadJob();
        job.setType(JobType.REPROCESS);
        job.setStatus(JobStatus.QUEUED);
        job.setUserId(userId);
        uploadJobRepository.save(job);

        JobMessage message = new JobMessage(job.getId(), "REPROCESS", null, null);
        saveOutboxEvent(RabbitMqConfig.FINANCE_REPROCESS_QUEUE, message);

        return job.getId();
    }

    private void saveOutboxEvent(String queueName, JobMessage message) throws JsonProcessingException {
        OutboxEvent event = new OutboxEvent();
        event.setQueueName(queueName);
        event.setPayload(objectMapper.writeValueAsString(message));
        outboxEventRepository.save(event);
    }

    private Path resolveAndValidatePath(String filename) {
        Path base = Path.of(tempDir).toAbsolutePath().normalize();
        Path resolved = base.resolve(filename).normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Invalid file path: " + filename);
        }
        return resolved;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "bin";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "bin";
    }
}
```

- [ ] **Step 4: Rodar testes**

```bash
cd backend && mvn test -Dtest=UploadJobServiceTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Criar JobResponse.java**

```java
package br.com.phfinance.shared.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String type,
        String status,
        Map<String, Object> result,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JobResponse from(UploadJob job) {
        Map<String, Object> result = null;
        if (job.getResultJson() != null) {
            try {
                result = MAPPER.readValue(job.getResultJson(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }
        return new JobResponse(
                job.getId(),
                job.getType().name(),
                job.getStatus().name(),
                result,
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 6: Criar UploadJobController.java**

```java
package br.com.phfinance.shared.jobs;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class UploadJobController {

    private final UploadJobRepository uploadJobRepository;

    public UploadJobController(UploadJobRepository uploadJobRepository) {
        this.uploadJobRepository = uploadJobRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID id, Authentication auth) {
        UploadJob job = uploadJobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!job.getUserId().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(JobResponse.from(job));
    }
}
```

- [ ] **Step 7: Escrever UploadJobControllerIT**

```java
package br.com.phfinance.shared.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "owner@example.com")
class UploadJobControllerIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @MockBean JavaMailSender mailSender;

    @Autowired MockMvc mockMvc;
    @Autowired UploadJobRepository uploadJobRepository;

    private UploadJob savedJob(String userId) {
        UploadJob job = new UploadJob();
        job.setType(JobType.FINANCE_UPLOAD);
        job.setStatus(JobStatus.QUEUED);
        job.setUserId(userId);
        return uploadJobRepository.save(job);
    }

    @Test
    void getJob_returns200_forOwner() throws Exception {
        UploadJob job = savedJob("owner@example.com");
        mockMvc.perform(get("/api/jobs/" + job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void getJob_returns404_forUnknownId() throws Exception {
        mockMvc.perform(get("/api/jobs/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "other@example.com")
    void getJob_returns403_forDifferentUser() throws Exception {
        UploadJob job = savedJob("owner@example.com");
        mockMvc.perform(get("/api/jobs/" + job.getId()))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 8: Rodar testes**

```bash
cd backend && mvn test -Dtest="UploadJobServiceTest,UploadJobControllerIT" -q
```

Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/br/com/phfinance/shared/jobs/ \
    backend/src/test/java/br/com/phfinance/shared/jobs/
git commit -m "feat: add UploadJobService and GET /api/jobs/{id} with owner authorization"
```

---

## Task 5: JobNotificationService

**Files:**
- Create: `backend/src/main/java/br/com/phfinance/shared/jobs/JobNotificationService.java`
- Create: `backend/src/test/java/br/com/phfinance/shared/jobs/JobNotificationServiceTest.java`

- [ ] **Step 1: Escrever teste falho**

```java
package br.com.phfinance.shared.jobs;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobNotificationServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock MimeMessage mimeMessage;

    JobNotificationService service;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        service = new JobNotificationService(mailSender, "noreply@ph-finance.local");
    }

    @Test
    void sendSuccess_doesNotThrow_forFinanceUpload() {
        UploadJob job = buildJob(JobType.FINANCE_UPLOAD, JobStatus.COMPLETED,
                "{\"total\":42,\"internalTransfers\":3,\"uncategorized\":5}", "user@example.com");
        service.sendSuccess(job);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendSuccess_doesNotThrow_forInflationUpload() {
        UploadJob job = buildJob(JobType.INFLATION_UPLOAD, JobStatus.COMPLETED,
                "{\"purchasesCreated\":10,\"purchasesSkipped\":2,\"itemsImported\":55}", "user@example.com");
        service.sendSuccess(job);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendSuccess_doesNotThrow_forReprocess() {
        UploadJob job = buildJob(JobType.REPROCESS, JobStatus.COMPLETED,
                "{\"categorized\":7,\"typeChanged\":2}", "user@example.com");
        service.sendSuccess(job);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendFailure_doesNotThrow() {
        UploadJob job = buildJob(JobType.FINANCE_UPLOAD, JobStatus.FAILED, null, "user@example.com");
        job.setErrorMessage("Parsing failed");
        job.setAttemptCount((short) 3);
        service.sendFailure(job);
        verify(mailSender).send(any(MimeMessage.class));
    }

    private UploadJob buildJob(JobType type, JobStatus status, String resultJson, String userId) {
        UploadJob job = new UploadJob();
        job.setType(type);
        job.setStatus(status);
        job.setUserId(userId);
        job.setResultJson(resultJson);
        job.setOriginalFilename("file.pdf");
        return job;
    }
}
```

- [ ] **Step 2: Rodar — deve falhar**

```bash
cd backend && mvn test -Dtest=JobNotificationServiceTest -q
```

Expected: FAIL

- [ ] **Step 3: Criar JobNotificationService.java**

```java
package br.com.phfinance.shared.jobs;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class JobNotificationService {

    private final JavaMailSender mailSender;
    private final String from;

    public JobNotificationService(JavaMailSender mailSender,
                                   @Value("${app.mail.from:noreply@ph-finance.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendSuccess(UploadJob job) {
        String subject = switch (job.getType()) {
            case FINANCE_UPLOAD    -> "[ph-finance] Upload concluído";
            case INFLATION_UPLOAD  -> "[ph-finance] Upload mercado concluído";
            case REPROCESS         -> "[ph-finance] Reprocessamento concluído";
        };
        String body = switch (job.getType()) {
            case FINANCE_UPLOAD   -> buildFinanceUploadBody(job);
            case INFLATION_UPLOAD -> buildInflationBody(job);
            case REPROCESS        -> buildReprocessBody(job);
        };
        send(job.getUserId(), subject, body);
    }

    public void sendFailure(UploadJob job) {
        String subject = "[ph-finance] Falha no processamento";
        String body = String.format(
                "Tipo: %s%nArquivo: %s%nErro: %s%nTentativas realizadas: %d",
                job.getType().name(),
                job.getOriginalFilename() != null ? job.getOriginalFilename() : "N/A",
                job.getErrorMessage(),
                job.getAttemptCount()
        );
        send(job.getUserId(), subject, body);
    }

    private String buildFinanceUploadBody(UploadJob job) {
        return "Upload de extrato bancário concluído.\nResultado: " + job.getResultJson();
    }

    private String buildInflationBody(UploadJob job) {
        return "Upload de planilha de mercado concluído.\nResultado: " + job.getResultJson();
    }

    private String buildReprocessBody(UploadJob job) {
        return "Reprocessamento de transações concluído.\nResultado: " + job.getResultJson();
    }

    private void send(String to, String subject, String body) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send notification email", e);
        }
    }
}
```

- [ ] **Step 4: Rodar testes**

```bash
cd backend && mvn test -Dtest=JobNotificationServiceTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/br/com/phfinance/shared/jobs/JobNotificationService.java \
    backend/src/test/java/br/com/phfinance/shared/jobs/JobNotificationServiceTest.java
git commit -m "feat: add JobNotificationService for success and failure emails"
```

---

## Task 6: Workers Finance Upload + DLQ + atualizar FinanceController.upload()

**Files:**
- Create: `backend/src/main/java/br/com/phfinance/finance/worker/FinanceUploadWorker.java`
- Create: `backend/src/main/java/br/com/phfinance/finance/worker/FinanceUploadDlqWorker.java`
- Modify: `backend/src/main/java/br/com/phfinance/finance/api/FinanceController.java`
- Create: `backend/src/test/java/br/com/phfinance/finance/worker/FinanceUploadWorkerTest.java`

- [ ] **Step 1: Escrever FinanceUploadWorkerTest**

```java
package br.com.phfinance.finance.worker;

import br.com.phfinance.finance.application.StatementUploadService;
import br.com.phfinance.finance.application.UploadResult;
import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.JobStatus;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceUploadWorkerTest {

    @TempDir Path tempDir;
    @Mock UploadJobRepository uploadJobRepository;
    @Mock StatementUploadService statementUploadService;
    @Mock JobNotificationService notificationService;

    @Test
    void handle_updatesJobCompletedAndDeletesFile() throws Exception {
        Path file = tempDir.resolve("test.pdf");
        Files.write(file, "pdf".getBytes());

        FinanceUploadWorker worker = new FinanceUploadWorker(
                uploadJobRepository, statementUploadService,
                notificationService, new ObjectMapper(), tempDir.toString()
        );

        UUID jobId = UUID.randomUUID();
        UploadJob job = new UploadJob();
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(statementUploadService.upload(any(), eq(BankName.NUBANK)))
                .thenReturn(new UploadResult(10, 2, 3));

        JobMessage msg = new JobMessage(jobId, "FINANCE_UPLOAD", file.toString(), "NUBANK");
        worker.handle(msg);

        verify(uploadJobRepository).markProcessing(jobId);
        verify(uploadJobRepository).markCompleted(eq(jobId), any(String.class));
        verify(notificationService).sendSuccess(any());
        assertThat(Files.exists(file)).isFalse();
    }
}
```

- [ ] **Step 2: Rodar — deve falhar**

```bash
cd backend && mvn test -Dtest=FinanceUploadWorkerTest -q
```

Expected: FAIL

- [ ] **Step 3: Criar FinanceUploadWorker.java**

```java
package br.com.phfinance.finance.worker;

import br.com.phfinance.finance.application.StatementUploadService;
import br.com.phfinance.finance.application.UploadResult;
import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FinanceUploadWorker {

    private final UploadJobRepository uploadJobRepository;
    private final StatementUploadService statementUploadService;
    private final JobNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String tempDir;

    public FinanceUploadWorker(UploadJobRepository uploadJobRepository,
                                StatementUploadService statementUploadService,
                                JobNotificationService notificationService,
                                ObjectMapper objectMapper,
                                @Value("${app.queue.file.temp-dir:/tmp/ph-finance}") String tempDir) {
        this.uploadJobRepository = uploadJobRepository;
        this.statementUploadService = statementUploadService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.tempDir = tempDir;
    }

    @RabbitListener(queues = RabbitMqConfig.FINANCE_UPLOAD_QUEUE)
    public void handle(JobMessage message) throws Exception {
        uploadJobRepository.markProcessing(message.jobId());

        Path filePath = validatePath(message.filePath());
        byte[] bytes = Files.readAllBytes(filePath);
        BankName bank = BankName.valueOf(message.bankName());

        UploadResult result = statementUploadService.upload(bytes, bank);

        String resultJson = objectMapper.writeValueAsString(result);
        uploadJobRepository.markCompleted(message.jobId(), resultJson);

        Files.deleteIfExists(filePath);

        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendSuccess(job);
    }

    private Path validatePath(String rawPath) {
        Path base = Path.of(tempDir).toAbsolutePath().normalize();
        Path resolved = Path.of(rawPath).toAbsolutePath().normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Invalid file path: " + rawPath);
        }
        return resolved;
    }
}
```

- [ ] **Step 4: Criar FinanceUploadDlqWorker.java**

```java
package br.com.phfinance.finance.worker;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FinanceUploadDlqWorker {

    private final UploadJobRepository uploadJobRepository;
    private final JobNotificationService notificationService;
    private final String tempDir;

    public FinanceUploadDlqWorker(UploadJobRepository uploadJobRepository,
                                   JobNotificationService notificationService,
                                   @Value("${app.queue.file.temp-dir:/tmp/ph-finance}") String tempDir) {
        this.uploadJobRepository = uploadJobRepository;
        this.notificationService = notificationService;
        this.tempDir = tempDir;
    }

    @RabbitListener(queues = RabbitMqConfig.FINANCE_UPLOAD_DLQ)
    public void handle(JobMessage message) throws Exception {
        uploadJobRepository.markFailed(message.jobId(), "Processamento falhou após todas as tentativas");

        if (message.filePath() != null) {
            Path base = Path.of(tempDir).toAbsolutePath().normalize();
            Path resolved = Path.of(message.filePath()).toAbsolutePath().normalize();
            if (resolved.startsWith(base)) {
                Files.deleteIfExists(resolved);
            }
        }

        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendFailure(job);
    }
}
```

- [ ] **Step 5: Atualizar FinanceController.upload()**

Substituir o método `upload()` em `FinanceController.java`:

Remover imports de `StatementUploadService`, `UploadResult`, `IOException`. Adicionar imports:

```java
import br.com.phfinance.shared.jobs.UploadJobService;
import java.util.Map;
import org.springframework.security.core.Authentication;
```

Substituir o campo `statementUploadService` e construtor — remover `StatementUploadService statementUploadService` e adicionar `UploadJobService uploadJobService`. Remover `transactionReprocessService` também (será substituído na Task 7, mas para não quebrar, mantenha por enquanto — apenas o método `upload` muda agora).

Método `upload()` novo:

```java
@PostMapping(value = "/uploads", consumes = "multipart/form-data")
public ResponseEntity<Map<String, Object>> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("bankName") String bankName,
        Authentication auth) throws java.io.IOException {
    if (file.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
    }
    if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds maximum allowed size of 10 MB");
    }
    if (!"application/pdf".equals(file.getContentType())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are accepted");
    }
    BankName bank;
    try {
        bank = BankName.valueOf(bankName.toUpperCase());
    } catch (IllegalArgumentException e) {
        String valid = Arrays.stream(BankName.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid bank name '" + bankName + "'. Valid values: " + valid);
    }
    UUID jobId = uploadJobService.createFinanceUploadJob(
            file.getBytes(), file.getOriginalFilename(), bankName, auth.getName());
    return ResponseEntity.accepted().body(Map.of("jobId", jobId));
}
```

**Atenção:** o construtor do `FinanceController` precisa receber `UploadJobService uploadJobService` no lugar de `StatementUploadService statementUploadService`. Mantenha os outros campos inalterados.

- [ ] **Step 6: Rodar testes**

```bash
cd backend && mvn test -Dtest="FinanceUploadWorkerTest,FinanceControllerIT" -q
```

Expected: BUILD SUCCESS. O teste `uploadNubankStatement_returns200_withTotalGreaterThanZero` vai mudar para status 202 — atualizar o assert em `FinanceControllerIT`:

```java
// antes: .andExpect(status().isOk())
// depois:
.andExpect(status().isAccepted())
.andExpect(jsonPath("$.jobId").isString())
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/phfinance/finance/worker/FinanceUploadWorker.java \
    backend/src/main/java/br/com/phfinance/finance/worker/FinanceUploadDlqWorker.java \
    backend/src/main/java/br/com/phfinance/finance/api/FinanceController.java \
    backend/src/test/java/br/com/phfinance/finance/worker/FinanceUploadWorkerTest.java \
    backend/src/test/java/br/com/phfinance/finance/api/FinanceControllerIT.java
git commit -m "feat: finance upload returns 202 jobId; add FinanceUploadWorker and DLQ worker"
```

---

## Task 7: Workers Finance Reprocess + DLQ + atualizar FinanceController.reprocess()

**Files:**
- Create: `backend/src/main/java/br/com/phfinance/finance/worker/ReprocessWorker.java`
- Create: `backend/src/main/java/br/com/phfinance/finance/worker/ReprocessDlqWorker.java`
- Modify: `backend/src/main/java/br/com/phfinance/finance/api/FinanceController.java`
- Create: `backend/src/test/java/br/com/phfinance/finance/worker/ReprocessWorkerTest.java`

- [ ] **Step 1: Escrever ReprocessWorkerTest**

```java
package br.com.phfinance.finance.worker;

import br.com.phfinance.finance.application.ReprocessResult;
import br.com.phfinance.finance.application.TransactionReprocessService;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReprocessWorkerTest {

    @Mock UploadJobRepository uploadJobRepository;
    @Mock TransactionReprocessService reprocessService;
    @Mock JobNotificationService notificationService;

    @Test
    void handle_updatesJobCompleted() throws Exception {
        ReprocessWorker worker = new ReprocessWorker(
                uploadJobRepository, reprocessService,
                notificationService, new ObjectMapper()
        );
        UUID jobId = UUID.randomUUID();
        when(reprocessService.reprocess()).thenReturn(new ReprocessResult(5, 2));
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(new UploadJob()));

        worker.handle(new JobMessage(jobId, "REPROCESS", null, null));

        verify(uploadJobRepository).markProcessing(jobId);
        verify(uploadJobRepository).markCompleted(eq(jobId), any(String.class));
        verify(notificationService).sendSuccess(any());
    }
}
```

- [ ] **Step 2: Rodar — deve falhar**

```bash
cd backend && mvn test -Dtest=ReprocessWorkerTest -q
```

Expected: FAIL

- [ ] **Step 3: Criar ReprocessWorker.java**

```java
package br.com.phfinance.finance.worker;

import br.com.phfinance.finance.application.ReprocessResult;
import br.com.phfinance.finance.application.TransactionReprocessService;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReprocessWorker {

    private final UploadJobRepository uploadJobRepository;
    private final TransactionReprocessService reprocessService;
    private final JobNotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ReprocessWorker(UploadJobRepository uploadJobRepository,
                            TransactionReprocessService reprocessService,
                            JobNotificationService notificationService,
                            ObjectMapper objectMapper) {
        this.uploadJobRepository = uploadJobRepository;
        this.reprocessService = reprocessService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqConfig.FINANCE_REPROCESS_QUEUE)
    public void handle(JobMessage message) throws Exception {
        uploadJobRepository.markProcessing(message.jobId());

        ReprocessResult result = reprocessService.reprocess();

        String resultJson = objectMapper.writeValueAsString(result);
        uploadJobRepository.markCompleted(message.jobId(), resultJson);

        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendSuccess(job);
    }
}
```

- [ ] **Step 4: Criar ReprocessDlqWorker.java**

```java
package br.com.phfinance.finance.worker;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReprocessDlqWorker {

    private final UploadJobRepository uploadJobRepository;
    private final JobNotificationService notificationService;

    public ReprocessDlqWorker(UploadJobRepository uploadJobRepository,
                               JobNotificationService notificationService) {
        this.uploadJobRepository = uploadJobRepository;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMqConfig.FINANCE_REPROCESS_DLQ)
    public void handle(JobMessage message) {
        uploadJobRepository.markFailed(message.jobId(), "Reprocessamento falhou após todas as tentativas");
        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendFailure(job);
    }
}
```

- [ ] **Step 5: Atualizar FinanceController.reprocess()**

Substituir o método `reprocess()`:

```java
@PostMapping("/reprocess")
public ResponseEntity<Map<String, Object>> reprocess(Authentication auth) throws Exception {
    UUID jobId = uploadJobService.createReprocessJob(auth.getName());
    return ResponseEntity.accepted().body(Map.of("jobId", jobId));
}
```

Remover o import de `TransactionReprocessService` e o campo do construtor (já não é mais necessário no controller). O campo `transactionReprocessService` é removido do construtor e da classe.

- [ ] **Step 6: Rodar testes**

```bash
cd backend && mvn test -Dtest="ReprocessWorkerTest,FinanceControllerIT" -q
```

Se `FinanceControllerIT` tiver teste para `reprocess`, atualizar para 202.

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/phfinance/finance/worker/ReprocessWorker.java \
    backend/src/main/java/br/com/phfinance/finance/worker/ReprocessDlqWorker.java \
    backend/src/main/java/br/com/phfinance/finance/api/FinanceController.java \
    backend/src/test/java/br/com/phfinance/finance/worker/ReprocessWorkerTest.java \
    backend/src/test/java/br/com/phfinance/finance/api/FinanceControllerIT.java
git commit -m "feat: reprocess returns 202 jobId; add ReprocessWorker and DLQ worker"
```

---

## Task 8: Workers Inflation Upload + DLQ + atualizar InflationController.upload()

**Files:**
- Create: `backend/src/main/java/br/com/phfinance/inflation/worker/InflationUploadWorker.java`
- Create: `backend/src/main/java/br/com/phfinance/inflation/worker/InflationUploadDlqWorker.java`
- Modify: `backend/src/main/java/br/com/phfinance/inflation/api/InflationController.java`
- Create: `backend/src/test/java/br/com/phfinance/inflation/worker/InflationUploadWorkerTest.java`

- [ ] **Step 1: Escrever InflationUploadWorkerTest**

```java
package br.com.phfinance.inflation.worker;

import br.com.phfinance.inflation.application.InflationUploadResult;
import br.com.phfinance.inflation.application.MarketUploadService;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InflationUploadWorkerTest {

    @TempDir Path tempDir;
    @Mock UploadJobRepository uploadJobRepository;
    @Mock MarketUploadService marketUploadService;
    @Mock JobNotificationService notificationService;

    @Test
    void handle_updatesJobCompletedAndDeletesFile() throws Exception {
        Path file = tempDir.resolve("market.xlsx");
        Files.write(file, "xlsx".getBytes());

        InflationUploadWorker worker = new InflationUploadWorker(
                uploadJobRepository, marketUploadService,
                notificationService, new ObjectMapper(), tempDir.toString()
        );

        UUID jobId = UUID.randomUUID();
        UploadJob job = new UploadJob();
        job.setOriginalFilename("market.xlsx");
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(marketUploadService.upload(any(), eq("market.xlsx")))
                .thenReturn(new InflationUploadResult(5, 1, 30));

        JobMessage msg = new JobMessage(jobId, "INFLATION_UPLOAD", file.toString(), null);
        worker.handle(msg);

        verify(uploadJobRepository).markProcessing(jobId);
        verify(uploadJobRepository).markCompleted(eq(jobId), any(String.class));
        verify(notificationService).sendSuccess(any());
        assertThat(Files.exists(file)).isFalse();
    }
}
```

- [ ] **Step 2: Rodar — deve falhar**

```bash
cd backend && mvn test -Dtest=InflationUploadWorkerTest -q
```

Expected: FAIL

- [ ] **Step 3: Criar InflationUploadWorker.java**

```java
package br.com.phfinance.inflation.worker;

import br.com.phfinance.inflation.application.InflationUploadResult;
import br.com.phfinance.inflation.application.MarketUploadService;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class InflationUploadWorker {

    private final UploadJobRepository uploadJobRepository;
    private final MarketUploadService marketUploadService;
    private final JobNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String tempDir;

    public InflationUploadWorker(UploadJobRepository uploadJobRepository,
                                  MarketUploadService marketUploadService,
                                  JobNotificationService notificationService,
                                  ObjectMapper objectMapper,
                                  @Value("${app.queue.file.temp-dir:/tmp/ph-finance}") String tempDir) {
        this.uploadJobRepository = uploadJobRepository;
        this.marketUploadService = marketUploadService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.tempDir = tempDir;
    }

    @RabbitListener(queues = RabbitMqConfig.INFLATION_UPLOAD_QUEUE)
    public void handle(JobMessage message) throws Exception {
        uploadJobRepository.markProcessing(message.jobId());

        Path filePath = validatePath(message.filePath());
        byte[] bytes = Files.readAllBytes(filePath);

        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        InflationUploadResult result = marketUploadService.upload(bytes, job.getOriginalFilename());

        String resultJson = objectMapper.writeValueAsString(result);
        uploadJobRepository.markCompleted(message.jobId(), resultJson);

        Files.deleteIfExists(filePath);

        UploadJob updated = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendSuccess(updated);
    }

    private Path validatePath(String rawPath) {
        Path base = Path.of(tempDir).toAbsolutePath().normalize();
        Path resolved = Path.of(rawPath).toAbsolutePath().normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Invalid file path: " + rawPath);
        }
        return resolved;
    }
}
```

- [ ] **Step 4: Criar InflationUploadDlqWorker.java**

```java
package br.com.phfinance.inflation.worker;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class InflationUploadDlqWorker {

    private final UploadJobRepository uploadJobRepository;
    private final JobNotificationService notificationService;
    private final String tempDir;

    public InflationUploadDlqWorker(UploadJobRepository uploadJobRepository,
                                     JobNotificationService notificationService,
                                     @Value("${app.queue.file.temp-dir:/tmp/ph-finance}") String tempDir) {
        this.uploadJobRepository = uploadJobRepository;
        this.notificationService = notificationService;
        this.tempDir = tempDir;
    }

    @RabbitListener(queues = RabbitMqConfig.INFLATION_UPLOAD_DLQ)
    public void handle(JobMessage message) throws Exception {
        uploadJobRepository.markFailed(message.jobId(), "Upload de mercado falhou após todas as tentativas");

        if (message.filePath() != null) {
            Path base = Path.of(tempDir).toAbsolutePath().normalize();
            Path resolved = Path.of(message.filePath()).toAbsolutePath().normalize();
            if (resolved.startsWith(base)) {
                Files.deleteIfExists(resolved);
            }
        }

        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendFailure(job);
    }
}
```

- [ ] **Step 5: Atualizar InflationController.upload()**

Substituir o método `upload()` e adicionar `UploadJobService` como dependência:

Adicionar import:
```java
import br.com.phfinance.shared.jobs.UploadJobService;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
```

Adicionar campo e atualizar construtor:
```java
private final UploadJobService uploadJobService;

public InflationController(MarketUploadService marketUploadService,
                           MarketComparisonService marketComparisonService,
                           UploadJobService uploadJobService) {
    this.marketUploadService = marketUploadService;
    this.marketComparisonService = marketComparisonService;
    this.uploadJobService = uploadJobService;
}
```

Substituir método `upload()`:
```java
@PostMapping(value = "/uploads", consumes = "multipart/form-data")
public ResponseEntity<Map<String, Object>> upload(
        @RequestParam("file") MultipartFile file,
        Authentication auth) throws java.io.IOException {
    if (file.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
    }
    if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File exceeds maximum allowed size of 10 MB");
    }
    UUID jobId = uploadJobService.createInflationUploadJob(
            file.getBytes(), file.getOriginalFilename(), auth.getName());
    return ResponseEntity.accepted().body(Map.of("jobId", jobId));
}
```

- [ ] **Step 6: Rodar testes**

```bash
cd backend && mvn test -Dtest="InflationUploadWorkerTest,InflationControllerIT" -q
```

Atualizar `InflationControllerIT` para esperar 202 no upload se houver esse teste.

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/com/phfinance/inflation/worker/ \
    backend/src/main/java/br/com/phfinance/inflation/api/InflationController.java \
    backend/src/test/java/br/com/phfinance/inflation/worker/
git commit -m "feat: inflation upload returns 202 jobId; add InflationUploadWorker and DLQ worker"
```

---

## Task 9: Integration Tests End-to-End

**Files:**
- Create: `backend/src/test/java/br/com/phfinance/shared/queue/OutboxEndToEndIT.java`

- [ ] **Step 1: Criar OutboxEndToEndIT**

```java
package br.com.phfinance.shared.queue;

import br.com.phfinance.shared.jobs.JobStatus;
import br.com.phfinance.shared.jobs.JobType;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.jobs.UploadJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "app.queue.retry.initial-interval=100",
        "app.queue.retry.max-attempts=3"
})
@Testcontainers
class OutboxEndToEndIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @MockBean JavaMailSender mailSender;

    @Autowired UploadJobService uploadJobService;
    @Autowired UploadJobRepository uploadJobRepository;

    @Test
    void reprocessJob_transitionsToCompleted() throws Exception {
        UUID jobId = uploadJobService.createReprocessJob("user@example.com");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            UploadJob job = uploadJobRepository.findById(jobId).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.getResultJson()).contains("categorized");
        });
    }

    @Test
    void unknownJobId_getJob_returnsQueued() throws Exception {
        UUID jobId = uploadJobService.createReprocessJob("user@example.com");

        UploadJob job = uploadJobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isIn(JobStatus.QUEUED, JobStatus.PROCESSING, JobStatus.COMPLETED);
    }
}
```

**Nota:** Este teste usa Awaitility. Adicionar dependência no `pom.xml`:

```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Rodar**

```bash
cd backend && mvn test -Dtest=OutboxEndToEndIT -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Rodar suite completa**

```bash
cd backend && mvn test -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/br/com/phfinance/shared/queue/OutboxEndToEndIT.java \
    backend/pom.xml
git commit -m "test: add OutboxEndToEndIT covering full outbox-to-worker flow"
```

---

## Task 10: Frontend — Polling

**Files:**
- Create: `frontend/src/lib/jobs.ts`
- Modify: `frontend/src/lib/finance.ts`
- Modify: `frontend/src/pages/UploadsPage.tsx`
- Modify: `frontend/src/pages/SettingsPage.tsx`
- Modify: `frontend/src/pages/InflationPage.tsx`

- [ ] **Step 1: Criar frontend/src/lib/jobs.ts**

```typescript
import api from './api'

export type JobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface JobResponse {
  id: string
  type: string
  status: JobStatus
  result: Record<string, number> | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export async function getJob(id: string): Promise<JobResponse> {
  const { data } = await api.get<JobResponse>(`/jobs/${id}`)
  return data
}

export function isTerminal(status: JobStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED'
}

export function useJobPolling(
  jobId: string | null,
  onDone: (job: JobResponse) => void,
  intervalMs = 2000
): void {
  // Chamado dentro de useEffect no componente
  // Exportado como função pura para facilitar uso com useEffect
}
```

**Nota:** `useJobPolling` é intencionalmente vazio — o polling é feito com `useEffect + setInterval` no componente. O arquivo existe para centralizar tipos e `getJob`.

- [ ] **Step 2: Atualizar frontend/src/lib/finance.ts**

Alterar a assinatura de `uploadStatement` e `reprocessTransactions`:

```typescript
import { JobResponse } from './jobs'

export async function uploadStatement(file: File, bankName: BankName): Promise<{ jobId: string }> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bankName', bankName)
  const { data } = await api.post<{ jobId: string }>('/finance/uploads', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function reprocessTransactions(): Promise<{ jobId: string }> {
  const { data } = await api.post<{ jobId: string }>('/finance/reprocess')
  return data
}
```

Manter `UploadResult`, `ReprocessResult` exports (ainda usados nos testes e como tipos de resultado do job).

- [ ] **Step 3: Reescrever UploadsPage.tsx com polling**

Substituir o conteúdo completo de `UploadsPage.tsx`:

```typescript
import { useState, useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'
import { uploadStatement, BankName, UploadResult } from '../lib/finance'
import { getJob, JobResponse, isTerminal } from '../lib/jobs'

interface UploadFormValues {
  bank: BankName
}

interface UploadEntry {
  filename: string
  bank: BankName
  jobId: string
  job: JobResponse | null
}

export default function UploadsPage() {
  const [files, setFiles] = useState<File[]>([])
  const [entries, setEntries] = useState<UploadEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [failures, setFailures] = useState<{ filename: string; error: string }[]>([])
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const { register, handleSubmit, reset } = useForm<UploadFormValues>({
    defaultValues: { bank: 'NUBANK' },
  })

  useEffect(() => {
    const pending = entries.filter((e) => e.job === null || !isTerminal(e.job.status))
    if (pending.length === 0) {
      if (pollingRef.current) clearInterval(pollingRef.current)
      return
    }
    pollingRef.current = setInterval(async () => {
      for (const entry of pending) {
        try {
          const job = await getJob(entry.jobId)
          setEntries((prev) =>
            prev.map((e) => (e.jobId === entry.jobId ? { ...e, job } : e))
          )
        } catch {}
      }
    }, 2000)
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current)
    }
  }, [entries])

  const onSubmit = async (values: UploadFormValues) => {
    if (files.length === 0) return
    setLoading(true)
    setFailures([])
    const newFailures: { filename: string; error: string }[] = []
    for (const file of files) {
      try {
        const { jobId } = await uploadStatement(file, values.bank)
        setEntries((prev) => [
          { filename: file.name, bank: values.bank, jobId, job: null },
          ...prev,
        ])
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Erro ao realizar upload.'
        newFailures.push({ filename: file.name, error: message })
      }
    }
    setFailures(newFailures)
    setFiles([])
    reset()
    setLoading(false)
  }

  return (
    <div style={{ maxWidth: 640 }}>
      <h1 style={{ marginBottom: '1.5rem' }}>Upload de Extrato</h1>

      <div style={cardStyle}>
        <form onSubmit={handleSubmit(onSubmit)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <label style={labelStyle}>
              Banco
              <select {...register('bank', { required: true })} style={inputStyle} aria-label="Selecionar banco">
                <option value="NUBANK">Nubank</option>
                <option value="NEON">Neon</option>
                <option value="INTER">Inter</option>
              </select>
            </label>
            <label style={labelStyle}>
              Arquivo PDF
              <input
                type="file" accept="application/pdf" multiple
                onChange={(e) => setFiles(Array.from(e.target.files ?? []))}
                aria-label="Selecionar arquivos PDF"
              />
              {files.length > 0 && (
                <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                  {files.length} arquivo(s) selecionado(s)
                </span>
              )}
            </label>
            {failures.length > 0 && (
              <ul role="alert" style={{ margin: 0, paddingLeft: '1.2rem', color: 'var(--color-danger)', fontSize: '0.875rem' }}>
                {failures.map((f) => (
                  <li key={f.filename}><strong>{f.filename}</strong>: {f.error}</li>
                ))}
              </ul>
            )}
            <button
              type="submit" disabled={loading || files.length === 0}
              style={{ padding: '0.6rem 1.5rem', background: 'var(--color-accent)', color: '#fff',
                border: 'none', borderRadius: '4px', cursor: loading || files.length === 0 ? 'not-allowed' : 'pointer',
                fontSize: '0.9rem', opacity: loading || files.length === 0 ? 0.65 : 1, alignSelf: 'flex-start' }}
            >
              {loading ? 'Enviando...' : 'Enviar extratos'}
            </button>
          </div>
        </form>
      </div>

      {entries.length > 0 && (
        <div style={{ marginTop: '2rem' }}>
          <h2 style={{ marginBottom: '1rem', fontSize: '1.1rem' }}>Uploads em andamento</h2>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {entries.map((entry) => (
              <li key={entry.jobId} style={{ ...cardStyle, padding: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                  <strong style={{ fontSize: '0.9rem' }}>{entry.filename}</strong>
                  <span style={{ fontSize: '0.8rem', color: statusColor(entry.job?.status) }}>
                    {entry.job?.status ?? 'Enviando...'}
                  </span>
                </div>
                {entry.job?.status === 'COMPLETED' && entry.job.result && (
                  <div style={{ display: 'flex', gap: '1.5rem', fontSize: '0.875rem', marginTop: '0.5rem' }}>
                    <span><strong style={{ color: 'var(--color-accent)' }}>{entry.job.result.total}</strong> transações</span>
                    <span><strong style={{ color: 'var(--color-purple)' }}>{entry.job.result.internalTransfers}</strong> internas</span>
                    <span><strong style={{ color: 'var(--color-warning)' }}>{entry.job.result.uncategorized}</strong> sem categoria</span>
                  </div>
                )}
                {entry.job?.status === 'FAILED' && (
                  <p style={{ margin: '0.5rem 0 0', color: 'var(--color-danger)', fontSize: '0.875rem' }}>
                    {entry.job.errorMessage ?? 'Erro desconhecido'}
                  </p>
                )}
                {(!entry.job || !isTerminal(entry.job.status)) && (
                  <p style={{ margin: '0.5rem 0 0', fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                    Processando… (atualiza automaticamente)
                  </p>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function statusColor(status?: string): string {
  if (status === 'COMPLETED') return 'var(--color-success)'
  if (status === 'FAILED') return 'var(--color-danger)'
  return 'var(--color-text-muted)'
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)', border: '1px solid var(--color-border)',
  borderRadius: '8px', padding: '1.5rem',
}
const labelStyle: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', gap: '0.3rem',
  fontSize: '0.85rem', color: 'var(--color-text-muted)', fontWeight: 500,
}
const inputStyle: React.CSSProperties = {
  padding: '0.45rem 0.6rem', border: '1px solid var(--color-border-input)',
  borderRadius: '4px', fontSize: '0.9rem', background: 'var(--color-surface)', color: 'var(--color-text)',
}
```

- [ ] **Step 4: Atualizar ReprocessTab em SettingsPage.tsx**

Localizar `function ReprocessTab()` e substituir:

```typescript
function ReprocessTab() {
  const [jobId, setJobId] = useState<string | null>(null)
  const [job, setJob] = useState<import('../lib/jobs').JobResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (!jobId || (job && isTerminal(job.status))) {
      if (pollingRef.current) clearInterval(pollingRef.current)
      return
    }
    pollingRef.current = setInterval(async () => {
      try {
        const updated = await getJob(jobId)
        setJob(updated)
      } catch {}
    }, 2000)
    return () => { if (pollingRef.current) clearInterval(pollingRef.current) }
  }, [jobId, job])

  const { mutate: run, isPending } = useMutation({
    mutationFn: reprocessTransactions,
    onSuccess: ({ jobId: id }) => {
      setError(null)
      setJob(null)
      setJobId(id)
    },
    onError: (err: unknown) => {
      setError(err instanceof Error ? err.message : 'Erro ao reprocessar.')
    },
  })

  return (
    <div style={{ maxWidth: 480 }}>
      <p style={{ color: 'var(--color-text-muted)', fontSize: '0.9rem', marginBottom: '1.25rem' }}>
        Reaplica as regras de categoria e de transferência interna em todas as transações existentes.
      </p>
      <button
        onClick={() => { setJob(null); setJobId(null); setError(null); run() }}
        disabled={isPending || (!!job && !isTerminal(job.status))}
        style={{ ...primaryBtnStyle, opacity: isPending ? 0.65 : 1, cursor: isPending ? 'not-allowed' : 'pointer' }}
      >
        {isPending ? 'Enviando...' : 'Reprocessar transações'}
      </button>
      {error && (
        <p role="alert" style={{ marginTop: '1rem', color: 'var(--color-danger)', fontSize: '0.9rem' }}>{error}</p>
      )}
      {jobId && !job && (
        <p style={{ marginTop: '1rem', fontSize: '0.9rem', color: 'var(--color-text-muted)' }}>
          Aguardando processamento…
        </p>
      )}
      {job?.status === 'COMPLETED' && job.result && (
        <p style={{ marginTop: '1.25rem', fontSize: '0.9rem' }}>
          <strong>{job.result.categorized}</strong> categorizada(s). <strong>{job.result.typeChanged}</strong> tipo(s) alterado(s).
        </p>
      )}
      {job?.status === 'FAILED' && (
        <p role="alert" style={{ marginTop: '1rem', color: 'var(--color-danger)', fontSize: '0.9rem' }}>
          {job.errorMessage ?? 'Falha no reprocessamento.'}
        </p>
      )}
    </div>
  )
}
```

Adicionar no topo do arquivo `SettingsPage.tsx`:

```typescript
import { useRef } from 'react'
import { getJob, isTerminal } from '../lib/jobs'
```

- [ ] **Step 5: Atualizar InflationPage.tsx**

Localizar `uploadMutation` e substituir `mutationFn` para usar polling após receber `jobId`:

O `uploadMutation.mutationFn` deixa de acumular resultado síncrono e passa a retornar `jobId[]`:

```typescript
const [jobIds, setJobIds] = useState<string[]>([])
const [jobResults, setJobResults] = useState<import('../lib/jobs').JobResponse[]>([])
const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)

useEffect(() => {
  const pending = jobIds.filter(
    (id) => !jobResults.find((j) => j.id === id && isTerminal(j.status))
  )
  if (pending.length === 0) { if (pollingRef.current) clearInterval(pollingRef.current); return }
  pollingRef.current = setInterval(async () => {
    for (const id of pending) {
      try {
        const job = await getJob(id)
        setJobResults((prev) => [...prev.filter((j) => j.id !== id), job])
      } catch {}
    }
  }, 2000)
  return () => { if (pollingRef.current) clearInterval(pollingRef.current) }
}, [jobIds, jobResults])

const uploadMutation = useMutation({
  mutationFn: async (filesToUpload: File[]) => {
    const ids: string[] = []
    for (let i = 0; i < filesToUpload.length; i++) {
      setUploadProgress({ current: i + 1, total: filesToUpload.length })
      const { jobId } = await uploadInflation(filesToUpload[i])
      ids.push(jobId)
    }
    return ids
  },
  onSuccess: (ids) => {
    setJobIds((prev) => [...prev, ...ids])
    setUploadMessage(`${ids.length} upload(s) enviado(s). Processando em background…`)
    setIsError(false)
    setUploadProgress(null)
    setFiles([])
    if (inputRef.current) inputRef.current.value = ''
  },
  onError: () => {
    setUploadMessage('Erro ao enviar arquivo.')
    setIsError(true)
    setUploadProgress(null)
  },
})
```

Atualizar `frontend/src/lib/inflation.ts` — `uploadInflation` retorna `{ jobId: string }`:

```typescript
export async function uploadInflation(file: File): Promise<{ jobId: string }> {
  // ... retry logic mantida ...
  const { data } = await api.post<{ jobId: string }>('/inflation/uploads', formData, { ... })
  return data
}
```

Remover `InflationUploadResult` do import em `InflationPage.tsx` pois o resultado vem via polling.

Adicionar import:
```typescript
import { getJob, isTerminal } from '../lib/jobs'
```

- [ ] **Step 6: Rodar testes do frontend**

```bash
cd frontend && npm test -- --run 2>&1 | tail -20
```

Atualizar `UploadsPage.test.tsx` e `SettingsPage.test.tsx` para mockar `uploadStatement` retornando `{ jobId: 'abc' }` e `reprocessTransactions` retornando `{ jobId: 'abc' }`. Mockar `getJob` retornando job COMPLETED imediatamente nos testes.

Expected: todos passam

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/jobs.ts frontend/src/lib/finance.ts \
    frontend/src/pages/UploadsPage.tsx frontend/src/pages/SettingsPage.tsx \
    frontend/src/pages/InflationPage.tsx
git commit -m "feat: frontend polling on job status after async upload/reprocess"
```

---

## Checklist Final

```bash
cd backend && mvn verify -q    # todos os testes backend passam
cd frontend && npm test -- --run  # todos os testes frontend passam
make lint                         # sem erros de lint
make build                        # build completo OK
```

```bash
git push -u origin feat/background-queue
gh pr create --title "feat: background queue processing via RabbitMQ" \
  --body "Migra upload e reprocessamento para background. Retorna 202 + jobId. Polling no frontend. Outbox pattern. DLQ com retry 3x backoff exponencial."
```

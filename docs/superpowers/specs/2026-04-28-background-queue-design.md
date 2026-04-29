# PRD — Migração de Uploads e Reprocessamento para Background com RabbitMQ

**Data:** 2026-04-28  
**Status:** Aprovado  
**Método:** SDD (Spec Driven Development)

---

## 1. Contexto e Problema

Hoje os três endpoints abaixo executam de forma **síncrona** na thread HTTP:

| Endpoint | Service | Problema |
|----------|---------|----------|
| `POST /api/finance/uploads` | `StatementUploadService` | PDF parse + OCR (Tesseract) pode demorar 10-60s → risco de timeout HTTP |
| `POST /api/inflation/uploads` | `MarketUploadService` | Parse de planilha — aceitável hoje, mas bloqueia thread |
| `POST /api/finance/reprocess` | `TransactionReprocessService` | Itera TODAS as transações → cresce com o tempo |

**Objetivo:** mover todo processamento pesado para background usando RabbitMQ, sem alterar a lógica de negócio existente.

---

## 2. Decisões de Design

| Decisão | Escolha |
|---------|---------|
| Feedback ao usuário | Email (sucesso e falha) |
| Armazenamento temporário de arquivo | Filesystem local (`app.queue.file.temp-dir`), apagado após processamento |
| Retry em falha | 3 tentativas com backoff exponencial (5s → 25s → 125s), depois DLQ + email de erro |
| Localização dos workers | Mesmo processo Spring Boot, arquitetura permite separação futura |
| Concorrência | Configurável via properties, padrão 1 worker por fila |
| Tracking de status | Tabela `upload_jobs` no PostgreSQL |
| Atomicidade controller→fila | Outbox Pattern |
| Testes de integração | Testcontainers (RabbitMQ + PostgreSQL) |
| Autoria do job | `user_id` registrado em `upload_jobs` |

---

## 3. Arquitetura Geral

```
HTTP Request
     │
     ▼
Controller (POST /uploads, POST /reprocess)
     │  1. Salva arquivo em /tmp/ph-finance/<uuid>.<ext>
     │  2. INSERT upload_jobs (status=QUEUED)
     │  3. INSERT outbox_events (published=false)
     │  4. COMMIT transação
     │  5. Retorna 202 Accepted + { "jobId": "<uuid>" }
     │
     ▼
OutboxPublisher (@Scheduled, fixedDelay=1s)
     │  SELECT outbox_events WHERE published=false FOR UPDATE SKIP LOCKED
     │  Publica no RabbitMQ
     │  UPDATE published=true
     │
     ▼
RabbitMQ (Exchange: finance.direct)
  ├── finance.upload      → FinanceUploadWorker
  ├── inflation.upload    → InflationUploadWorker
  └── finance.reprocess   → ReprocessWorker
           │
           │  Worker (caminho feliz):
           │  1. UPDATE upload_jobs → PROCESSING
           │  2. Chama service existente
           │  3. UPDATE upload_jobs → COMPLETED + result_json
           │  4. Apaga arquivo do filesystem
           │  5. Envia email de sucesso
           │
           │  Worker (falha, até 3x backoff exponencial):
           │  └── Mensagem → DLQ (finance.*.dlq)
           │      DLQ Listener:
           │      1. UPDATE upload_jobs → FAILED + error_message
           │      2. Apaga arquivo do filesystem
           │      3. Envia email de erro
```

---

## 4. Modelo de Dados

### 4.1 Tabela `upload_jobs`

```sql
CREATE TABLE upload_jobs (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    type              VARCHAR(30) NOT NULL,   -- FINANCE_UPLOAD | INFLATION_UPLOAD | REPROCESS
    status            VARCHAR(20) NOT NULL,   -- QUEUED | PROCESSING | COMPLETED | FAILED
    user_id           VARCHAR(255) NOT NULL,
    file_path         VARCHAR(500),           -- NULL para REPROCESS
    original_filename VARCHAR(255),           -- NULL para REPROCESS
    result_json       JSONB,                  -- resultado ao completar
    error_message     TEXT,                   -- preenchido em FAILED
    attempt_count     SMALLINT    NOT NULL DEFAULT 0,  -- incrementado pelo worker a cada tentativa; DLQ listener lê o valor final
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_upload_jobs_status  ON upload_jobs(status);
CREATE INDEX idx_upload_jobs_user_id ON upload_jobs(user_id);
```

### 4.2 Tabela `outbox_events`

```sql
CREATE TABLE outbox_events (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_name VARCHAR(100) NOT NULL,
    payload    JSONB        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published  BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX idx_outbox_events_published ON outbox_events(published) WHERE published = false;
```

### 4.3 Payload da Mensagem RabbitMQ

```json
{
  "jobId":    "uuid",
  "type":     "FINANCE_UPLOAD",
  "filePath": "/tmp/ph-finance/abc123.pdf",
  "bankName": "NUBANK"
}
```

> `bankName` presente apenas para `FINANCE_UPLOAD`. `filePath` ausente para `REPROCESS`.

---

## 5. API

### 5.1 Endpoints alterados

| Método | Path | Antes | Depois |
|--------|------|-------|--------|
| `POST` | `/api/finance/uploads` | `200 UploadResult` | `202 { jobId }` |
| `POST` | `/api/inflation/uploads` | `200 InflationUploadResult` | `202 { jobId }` |
| `POST` | `/api/finance/reprocess` | `200 ReprocessResult` | `202 { jobId }` |

### 5.2 Endpoint novo

```
GET /api/jobs/{id}

Response 200:
{
  "id":           "uuid",
  "type":         "FINANCE_UPLOAD",
  "status":       "COMPLETED",
  "result":       { "total": 42, "internalTransfers": 3, "uncategorized": 5 },
  "errorMessage": null,
  "createdAt":    "2026-04-28T10:00:00Z",
  "updatedAt":    "2026-04-28T10:00:45Z"
}

Response 404: job não encontrado
Response 403: job pertence a outro usuário
```

---

## 6. Configuração RabbitMQ

### 6.1 Topologia de Filas

```
Exchange principal: finance.direct  (type=direct, durable=true)
Exchange de DLQ:    finance.dlx     (type=direct, durable=true)

Filas de trabalho:
  finance.upload       x-dead-letter-exchange=finance.dlx
  inflation.upload     x-dead-letter-exchange=finance.dlx
  finance.reprocess    x-dead-letter-exchange=finance.dlx

Filas DLQ:
  finance.upload.dlq
  inflation.upload.dlq
  finance.reprocess.dlq
```

### 6.2 Retry — Backoff Exponencial

```
Tentativa 1 → falha → aguarda 5s
Tentativa 2 → falha → aguarda 25s
Tentativa 3 → falha → aguarda 125s
Esgotou     → mensagem publicada na DLQ
```

Implementado via `RetryInterceptor` do Spring AMQP com `ExponentialBackOffPolicy`. O listener é invocado até 3 vezes antes do Spring enviar a mensagem ao DLQ — `attempt_count` é incrementado no início de cada invocação pelo worker.

### 6.3 Properties

```properties
# Concorrência
app.queue.consumer.concurrency=1

# Retry
app.queue.retry.max-attempts=3
app.queue.retry.initial-interval=5000
app.queue.retry.multiplier=5.0

# Filesystem
app.queue.file.temp-dir=/tmp/ph-finance

# RabbitMQ (Spring padrão)
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=5672
spring.rabbitmq.username=${RABBITMQ_USER:finance}
spring.rabbitmq.password=${RABBITMQ_PASSWORD}
```

### 6.4 docker-compose.yml — Novo Serviço

```yaml
rabbitmq:
  image: rabbitmq:3.13-management-alpine
  ports:
    - "5672:5672"
    - "15672:15672"
  environment:
    RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-finance}
    RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
  healthcheck:
    test: ["CMD", "rabbitmq-diagnostics", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5
```

O serviço `backend` deve declarar `depends_on: rabbitmq: condition: service_healthy`.

---

## 7. Emails

| Evento | Assunto | Conteúdo |
|--------|---------|----------|
| `FINANCE_UPLOAD` COMPLETED | `[ph-finance] Upload concluído` | Banco, total importado, transferências internas, sem categoria |
| `INFLATION_UPLOAD` COMPLETED | `[ph-finance] Upload mercado concluído` | Compras criadas, compras ignoradas, itens importados |
| `REPROCESS` COMPLETED | `[ph-finance] Reprocessamento concluído` | Transações categorizadas, tipos alterados |
| Qualquer FAILED | `[ph-finance] Falha no processamento` | Tipo do job, nome do arquivo, mensagem de erro, tentativas realizadas |

---

## 8. Segurança

- `file_path` nunca exposto via API — armazenado somente no banco
- Worker valida que o path está dentro de `app.queue.file.temp-dir` antes de ler (prevenção de path traversal)
- `GET /api/jobs/{id}` retorna `403` se `user_id` do job ≠ usuário autenticado

---

## 9. Estrutura de Pacotes Novos

```
shared/
  queue/
    RabbitMqConfig.java           ← declara exchanges, filas, DLQs, bindings
    JobMessage.java               ← record: payload da mensagem
    OutboxEvent.java              ← entidade JPA
    OutboxEventRepository.java
    OutboxPublisher.java          ← @Scheduled, publica mensagens pendentes

  jobs/
    UploadJob.java                ← entidade JPA
    UploadJobRepository.java
    UploadJobService.java         ← cria job, salva arquivo, insere outbox event
    UploadJobController.java      ← GET /api/jobs/{id}
    JobStatus.java                ← enum: QUEUED, PROCESSING, COMPLETED, FAILED
    JobType.java                  ← enum: FINANCE_UPLOAD, INFLATION_UPLOAD, REPROCESS
    JobNotificationService.java   ← envia emails por tipo/status

finance/
  worker/
    FinanceUploadWorker.java      ← @RabbitListener finance.upload
    FinanceUploadDlqWorker.java   ← @RabbitListener finance.upload.dlq
    ReprocessWorker.java          ← @RabbitListener finance.reprocess
    ReprocessDlqWorker.java       ← @RabbitListener finance.reprocess.dlq

inflation/
  worker/
    InflationUploadWorker.java    ← @RabbitListener inflation.upload
    InflationUploadDlqWorker.java ← @RabbitListener inflation.upload.dlq
```

**Sem alterações** em `StatementUploadService`, `MarketUploadService`, `TransactionReprocessService`.

---

## 10. Testes

### Cobertura alvo

| Camada | Tipo | Cobertura |
|--------|------|-----------|
| Domain | Unit | 100% |
| Workers, Services, Outbox, Config | Unit + IT | ≥ 80% |

### Casos obrigatórios

| Classe | Tipo | Cenários |
|--------|------|---------|
| `OutboxPublisher` | Unit | Publica pendentes, marca `published=true`, ignora já publicados |
| `RabbitMqConfig` | Unit | Filas, DLQs e bindings declarados corretamente |
| `UploadJobService` | Unit | Cria job QUEUED, salva arquivo, insere outbox event |
| `FinanceUploadWorker` | Unit | Chama `StatementUploadService`, atualiza COMPLETED, apaga arquivo |
| `InflationUploadWorker` | Unit | Chama `MarketUploadService`, atualiza COMPLETED, apaga arquivo |
| `ReprocessWorker` | Unit | Chama `TransactionReprocessService`, atualiza COMPLETED |
| `JobNotificationService` | Unit | Email correto por tipo e status |
| `UploadJobController` | IT | `GET /api/jobs/{id}` → 200, 404, 403 |
| `FinanceUploadWorker` | IT | Mensagem consumida → job COMPLETED → arquivo apagado |
| `InflationUploadWorker` | IT | Mensagem consumida → job COMPLETED |
| `ReprocessWorker` | IT | Mensagem consumida → job COMPLETED |
| `DLQ listeners` | IT | 3 falhas → job FAILED → email enviado → arquivo apagado |
| Outbox end-to-end | IT | TX commit → evento publicado → job processado |

### Infraestrutura de Testes

```java
// AbstractIT.java
@Container
static RabbitMQContainer rabbitMQ =
    new RabbitMQContainer("rabbitmq:3.13-alpine");

@Container
static PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:16-alpine");
```

---

## 11. Tarefas para Desenvolvedores

### Tarefa 1 — Infraestrutura Base
**Responsável:** Dev Backend  
**Estimativa:** 0.5 dia

- Adicionar `spring-boot-starter-amqp` no `pom.xml`
- Adicionar `rabbitmq:3.13-management-alpine` no `docker-compose.yml`
- Criar `RabbitMqConfig.java` (exchanges, filas, DLQs, bindings)
- Adicionar properties de configuração em `application.properties`
- Atualizar `AbstractIT` com `RabbitMQContainer`

---

### Tarefa 2 — Modelo de Dados e Migrations Flyway
**Responsável:** Dev Backend  
**Estimativa:** 0.5 dia

- Criar migration `V{N}__create_upload_jobs.sql` (substituir N pela próxima versão disponível em `src/main/resources/db/migration/`)
- Criar migration `V{N+1}__create_outbox_events.sql`
- Criar entidades JPA: `UploadJob`, `OutboxEvent`
- Criar enums: `JobStatus`, `JobType`
- Criar repositories: `UploadJobRepository`, `OutboxEventRepository`

---

### Tarefa 3 — Outbox Publisher
**Responsável:** Dev Backend  
**Estimativa:** 0.5 dia

- Criar `JobMessage` (record com campos: `jobId`, `type`, `filePath`, `bankName`)
- Criar `OutboxPublisher` com `@Scheduled(fixedDelay=1000)`
  - `SELECT FOR UPDATE SKIP LOCKED` via query nativa
  - Publica no RabbitMQ
  - Marca `published=true`
- Testes unitários do `OutboxPublisher`

---

### Tarefa 4 — UploadJobService e Controller
**Responsável:** Dev Backend  
**Estimativa:** 1 dia

- Criar `UploadJobService`:
  - Salva arquivo em `app.queue.file.temp-dir/<uuid>.<ext>`
  - Valida path dentro do diretório configurado
  - Cria `UploadJob` (QUEUED) + `OutboxEvent` na mesma transação
- Criar `UploadJobController`:
  - `GET /api/jobs/{id}` com validação de `user_id`
- Testes unitários e IT do `UploadJobService` e `UploadJobController`

---

### Tarefa 5 — Workers Finance (Upload + DLQ)
**Responsável:** Dev Backend  
**Estimativa:** 1 dia

- Criar `FinanceUploadWorker` (`@RabbitListener("finance.upload")`):
  - Atualiza job → PROCESSING
  - Chama `StatementUploadService.upload(bytes, bank)`
  - Atualiza job → COMPLETED + `result_json`
  - Apaga arquivo
  - Envia email de sucesso
- Criar `FinanceUploadDlqWorker` (`@RabbitListener("finance.upload.dlq")`):
  - Atualiza job → FAILED + `error_message`
  - Apaga arquivo
  - Envia email de erro
- Atualizar `FinanceController.upload()` → retorna `202 { jobId }`
- Testes unitários e IT

---

### Tarefa 6 — Workers Finance (Reprocess + DLQ)
**Responsável:** Dev Backend  
**Estimativa:** 0.5 dia

- Criar `ReprocessWorker` (`@RabbitListener("finance.reprocess")`):
  - Atualiza job → PROCESSING
  - Chama `TransactionReprocessService.reprocess()`
  - Atualiza job → COMPLETED + `result_json`
  - Envia email de sucesso
- Criar `ReprocessDlqWorker`
- Atualizar `FinanceController.reprocess()` → retorna `202 { jobId }`
- Testes unitários e IT

---

### Tarefa 7 — Workers Inflation (Upload + DLQ)
**Responsável:** Dev Backend  
**Estimativa:** 0.5 dia

- Criar `InflationUploadWorker` (`@RabbitListener("inflation.upload")`):
  - Atualiza job → PROCESSING
  - Chama `MarketUploadService.upload(bytes, fileName)`
  - Atualiza job → COMPLETED + `result_json`
  - Apaga arquivo
  - Envia email de sucesso
- Criar `InflationUploadDlqWorker`
- Atualizar `InflationController.upload()` → retorna `202 { jobId }`
- Testes unitários e IT

---

### Tarefa 8 — JobNotificationService (Emails)
**Responsável:** Dev Backend  
**Estimativa:** 0.5 dia

- Criar `JobNotificationService` usando `JavaMailSender` (já configurado)
- Templates de email para cada combinação `JobType` × `JobStatus`
- Testes unitários com mock do `JavaMailSender`

---

### Tarefa 9 — Teste End-to-End do Outbox
**Responsável:** Dev Backend  
**Estimativa:** 0.5 dia

- IT que cobre fluxo completo:
  - `POST /api/finance/uploads` → `202 { jobId }`
  - Outbox publica → worker consome → job COMPLETED
  - `GET /api/jobs/{jobId}` → status COMPLETED com resultado
- IT de falha com DLQ:
  - Simula 3 falhas → job FAILED → email enviado

---

### Tarefa 10 — Frontend: adaptar chamadas de upload/reprocess
**Responsável:** Dev Frontend  
**Estimativa:** 1 dia

- Adaptar `POST /uploads` e `POST /reprocess` para receber `{ jobId }` em vez de resultado direto
- Implementar polling em `GET /api/jobs/{jobId}` (ex: a cada 2s até status terminal)
- Exibir estado: spinner enquanto QUEUED/PROCESSING, resultado ao COMPLETED, erro ao FAILED
- Remover dependência de resultado síncrono nos componentes de upload

---

**Estimativa total:** ~6 dias de desenvolvimento  
**Dependências entre tarefas:**
```
T1 (infra) → T2 (migrations) → T3 (outbox) → T4 (job service)
                                              → T5, T6, T7, T8 (workers, paralelo)
T4 + T5 + T6 + T7 + T8 → T9 (e2e)
T4 → T10 (frontend)
```

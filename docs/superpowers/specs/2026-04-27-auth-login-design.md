# PRD: Sistema de Autenticação e Login

**Data:** 2026-04-27  
**Status:** Aprovado  
**Projeto:** ph-finance  

---

## Visão Geral

Adicionar autenticação multi-usuário ao ph-finance com email + senha. Todas as rotas do frontend e endpoints do backend devem ser protegidos. Dados são compartilhados entre usuários, mas cada registro deve ter audit trail de criação e atualização.

---

## Decisões de Design

| Decisão | Escolha | Motivo |
|---------|---------|--------|
| Tipo de usuário | Multi-usuário | Múltiplas contas independentes |
| Método de auth | Email + senha | Sem dependência externa |
| Estratégia de sessão | Spring Session JDBC (Postgres) | Sem Redis, sem infra extra |
| Armazenamento token frontend | Cookie `SESSION` HttpOnly | Seguro contra XSS |
| Cadastro | Auto-registro aberto | Qualquer pessoa pode criar conta |
| Isolamento de dados | Compartilhado + audit trail | `created_by` / `updated_by` por registro |
| Reset de senha | Email (Mailpit no dev) | Link com token UUID, expira 1h |

---

## Arquitetura

```
Frontend (React)              Backend (Spring Boot)          Postgres
─────────────────             ─────────────────────          ────────
/login          ──────────→   POST /api/auth/login      →    users
/register       ──────────→   POST /api/auth/register   →    spring_session
/forgot-password ─────────→   POST /api/auth/forgot-password
/reset-password ──────────→   POST /api/auth/reset-password  password_reset_tokens

[rotas protegidas]  ──────→   SecurityFilterChain
                              verifica SESSION cookie    →    spring_session
                    ←────     200 OK ou 401 Unauthorized
```

### Fluxo de Autenticação

1. `POST /api/auth/login` → Spring Security autentica credenciais
2. Sessão criada no Postgres, cookie `SESSION` HttpOnly enviado ao browser
3. Requisições seguintes enviam cookie automaticamente
4. Frontend chama `GET /api/auth/me` ao inicializar — `200 {id, email, name}` ou `401`
5. `401` em qualquer rota → redirect para `/login`

---

## Banco de Dados

### Tabela `users`

```sql
CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,  -- BCrypt hash, strength 12
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
```

### Tabelas Spring Session (auto-criadas)

```
spring_session            — id, creation_time, last_access_time, max_inactive_interval, principal_name
spring_session_attributes — session_primary_id, attribute_name, attribute_bytes
```

### Tabela `password_reset_tokens`

```sql
CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    token      VARCHAR(255) NOT NULL UNIQUE,  -- UUID v4
    expires_at TIMESTAMP NOT NULL,            -- now() + 1 hora
    used       BOOLEAN NOT NULL DEFAULT false
);
```

### Audit Trail nas Tabelas de Domínio

Adicionar via migration nas tabelas de domínio existentes:

```sql
-- Tabelas de finanças
ALTER TABLE transaction        ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE transaction        ADD COLUMN updated_by UUID REFERENCES users(id);
ALTER TABLE bank_account       ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE bank_account       ADD COLUMN updated_by UUID REFERENCES users(id);
ALTER TABLE internal_transfer  ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE internal_transfer  ADD COLUMN updated_by UUID REFERENCES users(id);

-- Tabelas de inflação
ALTER TABLE market_purchase    ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE market_purchase    ADD COLUMN updated_by UUID REFERENCES users(id);
ALTER TABLE market_item        ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE market_item        ADD COLUMN updated_by UUID REFERENCES users(id);

-- Tabelas compartilhadas
ALTER TABLE category               ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE category               ADD COLUMN updated_by UUID REFERENCES users(id);
ALTER TABLE recipient_category_rule ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE recipient_category_rule ADD COLUMN updated_by UUID REFERENCES users(id);
ALTER TABLE internal_account_rule  ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE internal_account_rule  ADD COLUMN updated_by UUID REFERENCES users(id);
```

---

## Endpoints Backend

### Públicos (sem autenticação)

| Método | Endpoint | Body | Resposta |
|--------|----------|------|----------|
| `POST` | `/api/auth/register` | `{email, name, password}` | `201 {id, email, name}` |
| `POST` | `/api/auth/login` | `{email, password}` | `200 {id, email, name}` + cookie SESSION |
| `POST` | `/api/auth/logout` | — | `204` + invalida sessão |
| `GET`  | `/api/auth/me` | — | `200 {id, email, name}` ou `401` |
| `POST` | `/api/auth/forgot-password` | `{email}` | `204` (sempre, mesmo email inexistente) |
| `POST` | `/api/auth/reset-password` | `{token, newPassword}` | `204` ou `400` |

### Protegidos

Todos os endpoints existentes (`/api/transactions/**`, `/api/inflation/**`, `/api/patrimony/**`, `/api/categories/**`) exigem sessão válida. Retornam `401` se não autenticado.

---

## Frontend

### Novas Páginas

| Rota | Componente | Descrição |
|------|-----------|-----------|
| `/login` | `LoginPage` | Formulário email + senha |
| `/register` | `RegisterPage` | Formulário nome + email + senha |
| `/forgot-password` | `ForgotPasswordPage` | Formulário email para reset |
| `/reset-password` | `ResetPasswordPage` | Nova senha (token via `?token=xxx`) |

### `PrivateRoute` Component

```tsx
// Comportamento:
// 1. Chama GET /api/auth/me ao montar
// 2. loading → exibe spinner
// 3. 401 → <Navigate to="/login" replace />
// 4. 200 → renderiza children
```

### `router.tsx` Atualizado

```
Rotas públicas:
  /login, /register, /forgot-password, /reset-password

Rotas protegidas (wrappadas em <PrivateRoute>):
  / (Dashboard), /transactions, /transactions/:id,
  /inflation, /patrimony, /settings, /uploads
```

### `AuthContext`

Contexto global com:
- `user: {id, email, name} | null`
- `login(email, password): Promise<void>`
- `logout(): Promise<void>`
- `isLoading: boolean`

---

## Segurança

| Aspecto | Configuração |
|---------|-------------|
| Hash de senha | BCrypt, strength 12 |
| Sessão timeout | 8 horas de inatividade |
| Cookie flags | `HttpOnly; SameSite=Strict` (dev) + `Secure` apenas em prod (HTTPS) |
| CSRF | Habilitado (SameSite=Strict mitiga em dev) |
| Rate limiting | `/api/auth/login` e `/api/auth/register`: 10 req/min por IP (Bucket4j) |
| Token de reset | UUID v4, expira 1h, uso único, deletado após uso |
| Email not found | `/api/auth/forgot-password` retorna `204` mesmo se email não existe (evita user enumeration) |

---

## Email (Mailpit)

**Configuração docker-compose.yml (dev):**

```yaml
mailpit:
  image: axllent/mailpit
  ports:
    - "1025:1025"   # SMTP
    - "8025:8025"   # UI web (http://localhost:8025)
```

**application.properties (dev):**
```properties
spring.mail.host=localhost
spring.mail.port=1025
```

**Template do email de reset:**
```
Assunto: Redefinição de senha - ph-finance
Corpo:   Clique no link para redefinir sua senha:
         http://localhost:3000/reset-password?token=<uuid>
         O link expira em 1 hora.
```

---

## Testes

### Backend

| Camada | Cobertura | O que testar |
|--------|-----------|-------------|
| Domain `User` | 100% | validação email, regras de senha, equals/hash |
| `UserService` | 100% | register (email duplicado), findByEmail |
| `AuthService` | 100% | login, logout, forgotPassword, resetPassword (token expirado, usado) |
| `AuthController` IT | 80% | todos os endpoints com MockMvc, cenários de erro |

### Frontend

| Componente | O que testar |
|-----------|-------------|
| `PrivateRoute` | redirect em 401, render em 200, spinner em loading |
| `LoginPage` | submit correto, erro de credenciais, redirect pós-login |
| `RegisterPage` | submit correto, erro de email duplicado |
| `AuthContext` | estado inicial, login, logout |

---

## Estrutura de Arquivos Novos

### Backend

```
shared/
  auth/
    api/
      AuthController.java
      AuthRequest.java       -- login/register DTOs
      AuthResponse.java
    domain/
      User.java
      PasswordResetToken.java
      UserRepository.java
      PasswordResetTokenRepository.java
    application/
      AuthService.java
      EmailService.java
    config/
      SecurityConfig.java    -- SecurityFilterChain
      SessionConfig.java     -- Spring Session JDBC
```

### Frontend

```
src/
  contexts/
    AuthContext.tsx
  components/
    PrivateRoute.tsx
  pages/
    LoginPage.tsx
    RegisterPage.tsx
    ForgotPasswordPage.tsx
    ResetPasswordPage.tsx
```

---

## Dependências Novas

### Backend (`pom.xml`)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.session</groupId>
  <artifactId>spring-session-jdbc</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
  <groupId>com.bucket4j</groupId>
  <artifactId>bucket4j-core</artifactId>
  <version>8.10.1</version>
</dependency>
```

---

## Fora do Escopo

- Roles / RBAC (pode ser adicionado depois)
- OAuth / Social login
- 2FA / MFA
- Isolamento de dados por usuário (multi-tenant)
- Refresh token (não aplicável em session-based)

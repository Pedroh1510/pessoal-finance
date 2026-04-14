# Finance Control — Phase 1: Infrastructure Base

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Montar a infraestrutura base do projeto — Docker Compose, PostgreSQL com Flyway, Spring Boot skeleton com módulos, React + TypeScript skeleton com roteamento, e comandos de build/test/lint via Makefile.

**Architecture:** Monólito modular (DDD-lite) com pacotes por bounded context (`inflation/`, `finance/`, `patrimony/`, `shared/`). Backend Spring Boot 3.4 + PostgreSQL 16 via Flyway. Frontend React 19 + Vite + TanStack Query.

**Tech Stack:** Java 21, Spring Boot 3.4.3, PostgreSQL 16, Flyway, Testcontainers, Lombok, OpenAPI (Springdoc), React 19, TypeScript 5.7, Vite 6, TanStack Query 5, React Router 7, Recharts, Vitest.

---

## Task 1: Arquivos raiz do projeto

**Files:**
- Create: `.gitignore`
- Create: `.env.example`
- Create: `Makefile`

- [ ] **Step 1: Criar `.gitignore`**

```gitignore
# Env
.env
frontend/.env
frontend/.env.local

# Backend
backend/target/
backend/*.jar
backend/.mvn/repository/

# Frontend
frontend/node_modules/
frontend/dist/
frontend/.vite/

# IDE
.idea/
.vscode/
*.iml

# OS
.DS_Store
```

- [ ] **Step 2: Criar `.env.example`**

```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=finance
DB_SCHEMA=public
DB_USERNAME=finance_user
DB_PASSWORD=changeme

# Frontend (Vite dev proxy target)
VITE_API_URL=http://localhost:8080
```

- [ ] **Step 3: Criar `Makefile`**

```makefile
.PHONY: test build lint up down logs

test:
	cd backend && ./mvnw test
	cd frontend && npm test -- --run

build:
	cd backend && ./mvnw package -DskipTests
	cd frontend && npm run build

lint:
	cd frontend && npm run lint

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f
```

- [ ] **Step 4: Commitar**

```bash
git add .gitignore .env.example Makefile
git commit -m "chore: add project root config files"
```

---

## Task 2: Docker Compose e Dockerfiles

**Files:**
- Create: `docker-compose.yml`
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`

- [ ] **Step 1: Criar `docker-compose.yml`**

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ${DB_NAME:-finance}
      POSTGRES_USER: ${DB_USERNAME:-finance_user}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-changeme}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-finance_user} -d ${DB_NAME:-finance}"]
      interval: 5s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      DB_HOST: db
      DB_PORT: ${DB_PORT:-5432}
      DB_NAME: ${DB_NAME:-finance}
      DB_SCHEMA: ${DB_SCHEMA:-public}
      DB_USERNAME: ${DB_USERNAME:-finance_user}
      DB_PASSWORD: ${DB_PASSWORD:-changeme}
    depends_on:
      db:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "3000:80"
    depends_on:
      - backend

volumes:
  postgres_data:
```

- [ ] **Step 2: Criar `backend/Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline -q
COPY src ./src
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:21-jre
RUN apt-get update && \
    apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-por && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 3: Criar `frontend/nginx.conf`**

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location /api {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 4: Criar `frontend/Dockerfile`**

```dockerfile
FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json .
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

- [ ] **Step 5: Commitar**

```bash
git add docker-compose.yml backend/Dockerfile frontend/Dockerfile frontend/nginx.conf
git commit -m "chore: add Docker Compose and Dockerfiles"
```

---

## Task 3: Backend — pom.xml e Maven Wrapper

**Files:**
- Create: `backend/pom.xml`
- Create (gerado): `backend/mvnw`, `backend/.mvn/wrapper/maven-wrapper.properties`

- [ ] **Step 1: Criar `backend/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>

    <groupId>br.com.phfinance</groupId>
    <artifactId>finance-control</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>finance-control</name>
    <description>Personal finance control application</description>

    <properties>
        <java.version>21</java.version>
        <testcontainers.version>1.20.4</testcontainers.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.3</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Gerar Maven Wrapper**

```bash
cd backend && mvn wrapper:wrapper -Dmaven=3.9.9
```

Esperado: criação de `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`.

- [ ] **Step 3: Verificar download de dependências**

```bash
cd backend && ./mvnw dependency:go-offline -q
```

Esperado: sem erros, dependências baixadas para `~/.m2`.

- [ ] **Step 4: Commitar**

```bash
git add backend/pom.xml backend/mvnw backend/mvnw.cmd backend/.mvn
git commit -m "chore: add backend pom.xml and Maven wrapper"
```

---

## Task 4: Backend — Estrutura de pacotes e main class

**Files:**
- Create: `backend/src/main/java/br/com/phfinance/FinanceApplication.java`
- Create: `backend/src/main/java/br/com/phfinance/shared/config/OpenApiConfig.java`
- Create: `backend/src/main/resources/application.yml`

Estrutura de pacotes a criar (diretórios vazios com `.gitkeep`):
```
backend/src/main/java/br/com/phfinance/
├── inflation/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infra/
├── finance/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infra/
├── patrimony/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infra/
└── shared/
    ├── config/
    ├── fileparse/
    └── category/
```

- [ ] **Step 1: Criar estrutura de diretórios dos módulos**

```bash
cd backend/src/main/java/br/com/phfinance
for dir in \
  inflation/api inflation/application inflation/domain inflation/infra \
  finance/api finance/application finance/domain finance/infra \
  patrimony/api patrimony/application patrimony/domain patrimony/infra \
  shared/config shared/fileparse shared/category; do
  mkdir -p "$dir" && touch "$dir/.gitkeep"
done
```

- [ ] **Step 2: Criar `FinanceApplication.java`**

```java
package br.com.phfinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FinanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
```

- [ ] **Step 3: Criar `shared/config/OpenApiConfig.java`**

```java
package br.com.phfinance.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI financeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finance Control API")
                        .description("Personal finance control application API")
                        .version("1.0.0"));
    }
}
```

- [ ] **Step 4: Criar `src/main/resources/application.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:finance}?currentSchema=${DB_SCHEMA:public}
    username: ${DB_USERNAME:finance_user}
    password: ${DB_PASSWORD:changeme}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: ${DB_SCHEMA:public}
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    schemas: ${DB_SCHEMA:public}
    locations: classpath:db/migration

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 5: Commitar**

```bash
git add backend/src/main/java backend/src/main/resources/application.yml
git commit -m "feat: add Spring Boot application skeleton and module structure"
```

---

## Task 5: Backend — Flyway migrations (tabelas compartilhadas)

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_shared_tables.sql`

- [ ] **Step 1: Criar `V1__create_shared_tables.sql`**

```sql
CREATE TABLE IF NOT EXISTS category (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    color      VARCHAR(7)   NOT NULL,
    is_system  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS recipient_category_rule (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_pattern VARCHAR(255) NOT NULL,
    category_id       UUID        NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS internal_account_rule (
    id         UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(255) NOT NULL,
    type       VARCHAR(20)  NOT NULL CHECK (type IN ('NAME', 'CPF', 'CNPJ')),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Categorias padrão do sistema
INSERT INTO category (name, color, is_system) VALUES
    ('Alimentação',   '#FF6B6B', TRUE),
    ('Transporte',    '#4ECDC4', TRUE),
    ('Saúde',         '#45B7D1', TRUE),
    ('Educação',      '#96CEB4', TRUE),
    ('Lazer',         '#FFEAA7', TRUE),
    ('Moradia',       '#DDA0DD', TRUE),
    ('Investimentos', '#98FB98', TRUE),
    ('Salário',       '#87CEEB', TRUE),
    ('Transferência', '#D3D3D3', TRUE),
    ('Outros',        '#F0E68C', TRUE)
ON CONFLICT DO NOTHING;
```

- [ ] **Step 2: Commitar**

```bash
git add backend/src/main/resources/db/migration/
git commit -m "feat: add Flyway migration V1 - shared tables and default categories"
```

---

## Task 6: Backend — Smoke test com Testcontainers

**Files:**
- Create: `backend/src/test/java/br/com/phfinance/FinanceApplicationTest.java`

- [ ] **Step 1: Escrever o teste**

```java
package br.com.phfinance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class FinanceApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Verifica que o contexto Spring sobe, Flyway roda as migrations
        // e a conexão com o banco está OK
    }
}
```

- [ ] **Step 2: Executar o teste — deve PASSAR (contexto sobe com banco real)**

```bash
cd backend && ./mvnw test -Dtest=FinanceApplicationTest -q
```

Esperado: `BUILD SUCCESS`. Se falhar, verificar se Docker está rodando (Testcontainers precisa de Docker).

- [ ] **Step 3: Commitar**

```bash
git add backend/src/test/java/br/com/phfinance/FinanceApplicationTest.java
git commit -m "test: add smoke test - Spring context loads with Testcontainers"
```

---

## Task 7: Backend — Endpoint de health check (teste de integração)

**Files:**
- Create: `backend/src/test/java/br/com/phfinance/shared/HealthCheckIT.java`

O endpoint `/actuator/health` já existe via `spring-boot-starter-actuator`. Apenas o teste de integração precisa ser criado.

- [ ] **Step 1: Escrever o teste de integração**

```java
package br.com.phfinance.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HealthCheckIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

- [ ] **Step 2: Executar o teste — deve PASSAR**

```bash
cd backend && ./mvnw test -Dtest=HealthCheckIT -q
```

Esperado: `BUILD SUCCESS`.

- [ ] **Step 3: Executar todos os testes do backend**

```bash
cd backend && ./mvnw test -q
```

Esperado: `BUILD SUCCESS`, todos os testes passando.

- [ ] **Step 4: Commitar**

```bash
git add backend/src/test/java/br/com/phfinance/shared/HealthCheckIT.java
git commit -m "test: add health check integration test"
```

---

## Task 8: Frontend — Setup Vite + React + TypeScript

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/src/test/setup.ts`

- [ ] **Step 1: Criar `frontend/package.json`**

```json
{
  "name": "finance-control-frontend",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "lint": "eslint .",
    "preview": "vite preview",
    "test": "vitest"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router-dom": "^7.1.5",
    "@tanstack/react-query": "^5.65.0",
    "recharts": "^2.15.0",
    "react-hook-form": "^7.54.2"
  },
  "devDependencies": {
    "@eslint/js": "^9.18.0",
    "@types/react": "^19.0.6",
    "@types/react-dom": "^19.0.3",
    "@vitejs/plugin-react": "^4.3.4",
    "@testing-library/react": "^16.2.0",
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/user-event": "^14.5.2",
    "eslint": "^9.18.0",
    "eslint-plugin-react-hooks": "^5.0.0",
    "eslint-plugin-react-refresh": "^0.4.18",
    "jsdom": "^26.0.0",
    "typescript": "~5.7.2",
    "typescript-eslint": "^8.20.0",
    "vite": "^6.0.7",
    "vitest": "^3.0.4"
  }
}
```

- [ ] **Step 2: Criar `frontend/tsconfig.json`**

```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

- [ ] **Step 3: Criar `frontend/tsconfig.app.json`**

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "noUncheckedSideEffectImports": true
  },
  "include": ["src"]
}
```

- [ ] **Step 4: Criar `frontend/tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.node.tsbuildinfo",
    "target": "ES2022",
    "lib": ["ES2023"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "noUncheckedSideEffectImports": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 5: Criar `frontend/.env.example`**

```dotenv
VITE_API_URL=http://localhost:8080
```

- [ ] **Step 6: Criar `frontend/vite.config.ts`**

```typescript
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': {
          target: env.VITE_API_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
    },
  }
})
```

- [ ] **Step 7: Criar `frontend/index.html`**

```html
<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Finance Control</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 8: Criar `frontend/src/test/setup.ts`**

```typescript
import '@testing-library/jest-dom'
```

- [ ] **Step 9: Criar `frontend/eslint.config.js`**

```javascript
import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
)
```

Adicionar dependência faltante:

```bash
cd frontend && npm install --save-dev globals
```

- [ ] **Step 10: Instalar dependências**

```bash
cd frontend && npm install
```

Esperado: `node_modules/` criado sem erros.

- [ ] **Step 11: Verificar lint**

```bash
cd frontend && npm run lint
```

Esperado: sem erros (apenas warnings de `react-refresh` são aceitáveis para páginas skeleton).

- [ ] **Step 12: Commitar**

```bash
git add frontend/package.json frontend/package-lock.json frontend/tsconfig*.json frontend/vite.config.ts frontend/index.html frontend/eslint.config.js frontend/src/test/setup.ts frontend/.env.example
git commit -m "chore: add frontend Vite + React + TypeScript setup"
```

---

## Task 9: Frontend — Roteamento, Layout e páginas skeleton

**Files:**
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/router.tsx`
- Create: `frontend/src/components/Layout.tsx`
- Create: `frontend/src/pages/DashboardPage.tsx`
- Create: `frontend/src/pages/TransactionsPage.tsx`
- Create: `frontend/src/pages/InflationPage.tsx`
- Create: `frontend/src/pages/PatrimonyPage.tsx`
- Create: `frontend/src/pages/SettingsPage.tsx`
- Create: `frontend/src/pages/UploadsPage.tsx`
- Create: `frontend/src/components/Layout.test.tsx`

- [ ] **Step 1: Escrever o teste do Layout (RED)**

```typescript
// frontend/src/components/Layout.test.tsx
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Layout from './Layout'

const queryClient = new QueryClient()

function renderLayout() {
  const router = createMemoryRouter([
    {
      path: '/',
      element: <Layout />,
      children: [{ index: true, element: <div>content</div> }],
    },
  ])
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}

describe('Layout', () => {
  it('renders all navigation links', () => {
    renderLayout()
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Transações')).toBeInTheDocument()
    expect(screen.getByText('Inflação')).toBeInTheDocument()
    expect(screen.getByText('Patrimônio')).toBeInTheDocument()
    expect(screen.getByText('Uploads')).toBeInTheDocument()
    expect(screen.getByText('Configurações')).toBeInTheDocument()
  })

  it('renders outlet content', () => {
    renderLayout()
    expect(screen.getByText('content')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Executar o teste — deve FALHAR**

```bash
cd frontend && npm test -- --run src/components/Layout.test.tsx
```

Esperado: FAIL — `Layout` não existe ainda.

- [ ] **Step 3: Criar `frontend/src/components/Layout.tsx`**

```tsx
import { Outlet, NavLink } from 'react-router-dom'

const navItems = [
  { to: '/',             label: 'Dashboard',     end: true },
  { to: '/transactions', label: 'Transações' },
  { to: '/inflation',    label: 'Inflação' },
  { to: '/patrimony',    label: 'Patrimônio' },
  { to: '/uploads',      label: 'Uploads' },
  { to: '/settings',     label: 'Configurações' },
]

export default function Layout() {
  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <nav style={{ width: 220, padding: '1rem', borderRight: '1px solid #eee' }}>
        <h2 style={{ margin: '0 0 1rem' }}>Finance</h2>
        <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {navItems.map(({ to, label, end }) => (
            <li key={to} style={{ marginBottom: '0.5rem' }}>
              <NavLink
                to={to}
                end={end}
                style={({ isActive }) => ({
                  textDecoration: 'none',
                  color: isActive ? '#1a56db' : '#333',
                  fontWeight: isActive ? 600 : 400,
                })}
              >
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main style={{ flex: 1, padding: '1.5rem' }}>
        <Outlet />
      </main>
    </div>
  )
}
```

- [ ] **Step 4: Executar o teste — deve PASSAR**

```bash
cd frontend && npm test -- --run src/components/Layout.test.tsx
```

Esperado: PASS — 2 testes passando.

- [ ] **Step 5: Criar páginas skeleton**

`frontend/src/pages/DashboardPage.tsx`:
```tsx
export default function DashboardPage() {
  return <h1>Dashboard</h1>
}
```

`frontend/src/pages/TransactionsPage.tsx`:
```tsx
export default function TransactionsPage() {
  return <h1>Transações</h1>
}
```

`frontend/src/pages/InflationPage.tsx`:
```tsx
export default function InflationPage() {
  return <h1>Inflação</h1>
}
```

`frontend/src/pages/PatrimonyPage.tsx`:
```tsx
export default function PatrimonyPage() {
  return <h1>Patrimônio</h1>
}
```

`frontend/src/pages/SettingsPage.tsx`:
```tsx
export default function SettingsPage() {
  return <h1>Configurações</h1>
}
```

`frontend/src/pages/UploadsPage.tsx`:
```tsx
export default function UploadsPage() {
  return <h1>Uploads</h1>
}
```

- [ ] **Step 6: Criar `frontend/src/router.tsx`**

```tsx
import { createBrowserRouter } from 'react-router-dom'
import Layout from './components/Layout'
import DashboardPage from './pages/DashboardPage'
import TransactionsPage from './pages/TransactionsPage'
import InflationPage from './pages/InflationPage'
import PatrimonyPage from './pages/PatrimonyPage'
import SettingsPage from './pages/SettingsPage'
import UploadsPage from './pages/UploadsPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true,             element: <DashboardPage /> },
      { path: 'transactions',    element: <TransactionsPage /> },
      { path: 'inflation',       element: <InflationPage /> },
      { path: 'patrimony',       element: <PatrimonyPage /> },
      { path: 'settings',        element: <SettingsPage /> },
      { path: 'uploads',         element: <UploadsPage /> },
    ],
  },
])
```

- [ ] **Step 7: Criar `frontend/src/main.tsx`**

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { router } from './router'

const queryClient = new QueryClient()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </React.StrictMode>
)
```

- [ ] **Step 8: Executar todos os testes do frontend**

```bash
cd frontend && npm test -- --run
```

Esperado: PASS — todos os testes passando.

- [ ] **Step 9: Verificar build do frontend**

```bash
cd frontend && npm run build
```

Esperado: `dist/` gerado sem erros de TypeScript.

- [ ] **Step 10: Commitar**

```bash
git add frontend/src/
git commit -m "feat: add frontend routing, Layout component and page skeletons"
```

---

## Task 10: Verificação final

- [ ] **Step 1: Executar todos os testes do backend**

```bash
cd backend && ./mvnw test -q
```

Esperado: `BUILD SUCCESS`.

- [ ] **Step 2: Executar todos os testes do frontend**

```bash
cd frontend && npm test -- --run
```

Esperado: todos passando.

- [ ] **Step 3: Verificar build completo**

```bash
make build
```

Esperado: backend JAR gerado + frontend `dist/` gerado, sem erros.

- [ ] **Step 4: Subir o stack completo com Docker**

```bash
cp .env.example .env
make up
```

Esperado: containers `db`, `backend`, `frontend` rodando.

- [ ] **Step 5: Verificar health do backend**

```bash
curl http://localhost:8080/actuator/health
```

Esperado:
```json
{"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}
```

- [ ] **Step 6: Verificar Swagger UI**

Abrir no browser: `http://localhost:8080/swagger-ui.html`

Esperado: página do Swagger UI carrega com título "Finance Control API".

- [ ] **Step 7: Verificar frontend**

Abrir no browser: `http://localhost:3000`

Esperado: sidebar com todos os links de navegação, conteúdo da página "Dashboard" visível.

- [ ] **Step 8: Derrubar stack**

```bash
make down
```

- [ ] **Step 9: Commit final da fase**

```bash
git add .
git commit -m "chore: phase 1 complete - infrastructure base verified"
```

---

## Resumo dos arquivos criados nesta fase

```
.gitignore
.env.example
Makefile
docker-compose.yml
backend/
├── Dockerfile
├── pom.xml
├── mvnw
├── .mvn/wrapper/maven-wrapper.properties
└── src/
    ├── main/
    │   ├── java/br/com/phfinance/
    │   │   ├── FinanceApplication.java
    │   │   └── shared/config/OpenApiConfig.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/V1__create_shared_tables.sql
    └── test/
        └── java/br/com/phfinance/
            ├── FinanceApplicationTest.java
            └── shared/HealthCheckIT.java
frontend/
├── Dockerfile
├── nginx.conf
├── package.json
├── tsconfig.json
├── tsconfig.app.json
├── tsconfig.node.json
├── vite.config.ts
├── index.html
└── src/
    ├── main.tsx
    ├── router.tsx
    ├── components/
    │   ├── Layout.tsx
    │   └── Layout.test.tsx
    ├── pages/
    │   ├── DashboardPage.tsx
    │   ├── TransactionsPage.tsx
    │   ├── InflationPage.tsx
    │   ├── PatrimonyPage.tsx
    │   ├── SettingsPage.tsx
    │   └── UploadsPage.tsx
    └── test/
        └── setup.ts
```

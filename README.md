# PH Finance

[![CI](https://github.com/Pedroh1510/pessoal-finance/actions/workflows/ci.yml/badge.svg)](https://github.com/Pedroh1510/pessoal-finance/actions/workflows/ci.yml)
[![Gitleaks](https://img.shields.io/badge/security-gitleaks-green)](https://github.com/gitleaks/gitleaks)
[![OWASP](https://img.shields.io/badge/security-OWASP%20Dependency%20Check-blue)](https://owasp.org/www-project-dependency-check/)

Aplicação de controle financeiro pessoal para acompanhamento de transações bancárias, rastreamento de inflação em mercados e patrimônio.

## Funcionalidades

- **Finanças:** importação de extratos PDF (Nubank, Neon, Inter via OCR), categorização automática por regras, filtros por mês/banco/categoria/tipo
- **Inflação:** importação de notas fiscais XLSX, comparação de preços por NCM ao longo do tempo
- **Dashboard:** resumo mensal (receita, despesa, saldo), gráfico de categorias, histórico de 12 meses
- **Categorias:** sistema de categorias padrão + categorias personalizadas com cores
- **Patrimônio:** em desenvolvimento

## Stack

| Camada | Tecnologias |
|--------|-------------|
| Backend | Java 21, Spring Boot 3.4, Spring Data JPA, Flyway, PDFBox, Tesseract (OCR), Apache POI |
| Frontend | TypeScript 5.7, React 19, Vite 6, TanStack Query, React Router 7, Recharts |
| Banco de Dados | PostgreSQL 16 |
| Infra | Docker, Docker Compose, Nginx |

## Pré-requisitos

- Docker e Docker Compose
- (Desenvolvimento local) Java 21, Node 20+, PostgreSQL 16

## Como Rodar

### Com Docker (recomendado)

```bash
cp .env.example .env
# Edite .env se necessário

make up
```

Serviços disponíveis após `make up`:

| Serviço | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:5432 |

```bash
make logs   # Ver logs
make down   # Parar tudo
```

### Desenvolvimento Local

**Backend:**
```bash
cd backend
export DB_HOST=localhost DB_PORT=5432 DB_NAME=finance DB_USERNAME=finance_user DB_PASSWORD=changeme
./mvnw spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
VITE_API_URL=http://localhost:8080 npm run dev
```

## Variáveis de Ambiente

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=finance
DB_SCHEMA=public
DB_USERNAME=finance_user
DB_PASSWORD=changeme

VITE_API_URL=http://localhost:8080
```

## Testes e Qualidade

```bash
make test    # Testes Maven + Vitest
make lint    # ESLint frontend
make build   # Build completo (JAR + dist)
```

Cobertura mínima exigida:

| Camada | Mínimo |
|--------|--------|
| Domínio (entidades, serviços, casos de uso) | 100% |
| Demais camadas (controllers, repos, utils) | 80% |

## API

Documentação interativa disponível em `/swagger-ui.html` com todos os endpoints.

### Resumo dos endpoints

```
# Finanças
POST   /api/finance/uploads                   # Upload extrato PDF
GET    /api/finance/transactions              # Listar transações
PUT    /api/finance/transactions/:id/category # Categorizar transação
POST   /api/finance/reprocess                 # Re-aplicar regras

# Regras de categorização
GET|POST|DELETE  /api/finance/rules/recipient
GET|POST|DELETE  /api/finance/rules/internal-accounts

# Inflação
POST  /api/inflation/uploads    # Upload planilha XLSX
GET   /api/inflation/items      # Listar itens
GET   /api/inflation/comparison # Comparar preços por período

# Categorias
GET|POST|PUT|DELETE  /api/categories
```

## Estrutura do Projeto

```
finance/
├── backend/          # Spring Boot
│   └── src/main/java/br/com/phfinance/
│       ├── finance/      # Módulo de finanças
│       ├── inflation/    # Módulo de inflação
│       ├── patrimony/    # Módulo de patrimônio (em desenvolvimento)
│       └── shared/       # Categorias, configurações, rate limiting
├── frontend/         # React + TypeScript
│   └── src/
│       ├── pages/        # Dashboard, Transactions, Inflation, Patrimony
│       ├── components/   # Componentes reutilizáveis
│       └── lib/          # Clients de API
├── docker-compose.yml
├── Makefile
└── .env.example
```

## Workflow de Desenvolvimento

- Sempre criar branch antes de iniciar: `git checkout -b feat/nome-da-feature`
- Executar `make test && make lint && make build` antes de commitar
- Todas as mudanças via Pull Request — nunca push direto em `main`

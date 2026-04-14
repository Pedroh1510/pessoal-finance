# Finance Control — Design Spec

**Data:** 2026-04-14  
**Status:** Aprovado  
**Tipo:** Uso pessoal (single user)

---

## 1. Visão Geral

Aplicativo de controle financeiro pessoal com três módulos principais:

- **Inflação:** rastreamento de preços de itens de mercado via XLSX, comparativo entre períodos usando NCM como chave de identificação.
- **Finanças:** extração de transações de PDFs bancários (Nubank, Neon, Inter) via OCR, categorização automática e manual, detecção de transferências internas.
- **Patrimônio:** posição de CDB (manual), FIIs e Ações (alimentados automaticamente por notas de corretagem BTG).

---

## 2. Stack Tecnológica

| Camada | Tecnologia |
|--------|-----------|
| Backend | Java 21, Spring Boot 3, Spring Web, OpenAPI (Springdoc), Spring Data JPA, Lombok, Testcontainers |
| Banco de dados | PostgreSQL 16 |
| OCR | Apache PDFBox (extração de texto) + Tesseract (fallback para PDFs baseados em imagem), instalados no container do backend |
| Frontend | React + TypeScript, Vite, Node 24, TanStack Query, Recharts, React Hook Form, React Router |
| Infra | Docker + Docker Compose (3 serviços: `db`, `backend`, `frontend`) |
| Sem serviços pagos | Sem S3/cloud — arquivos em volume Docker local |

---

## 3. Arquitetura

### 3.1 Visão Macro

```
Frontend (React) → REST/JSON (OpenAPI) → Backend (Spring Boot) → PostgreSQL 16
```

### 3.2 Estrutura de Módulos (Monólito Modular / DDD-lite)

```
br.com.finance/
├── inflation/
│   ├── api/          (controllers)
│   ├── application/  (use cases / services)
│   ├── domain/       (entities, value objects, regras de negócio)
│   └── infra/        (repositories, parsers XLSX)
├── finance/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infra/        (repositories, parsers PDF por banco)
├── patrimony/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infra/        (repositories, parser nota BTG)
└── shared/
    ├── category/     (categorias e regras)
    ├── fileparse/    (PDFBox + Tesseract wrapper)
    └── config/       (OpenAPI, JPA, etc.)
```

Comunicação entre módulos ocorre exclusivamente via interfaces de serviço (`application/`), nunca acesso direto a repositórios de outros módulos.

### 3.3 Docker Compose

```
services:
  db        — postgres:16, porta 5432
  backend   — Spring Boot, porta 8080 (Tesseract instalado via Dockerfile)
  frontend  — React/Vite, porta 5173
```

**Dockerfile do backend inclui:**
```dockerfile
RUN apt-get install -y tesseract-ocr tesseract-ocr-por
```

### 3.4 Configuração via Variáveis de Ambiente

Toda configuração sensível ou de ambiente é feita via variáveis de ambiente (nunca hardcoded). O `.env` é usado pelo Docker Compose; o `application.yml` do Spring Boot lê via `${VAR:default}`.

**Variáveis obrigatórias:**

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `DB_HOST` | Host do banco | `db` |
| `DB_PORT` | Porta do banco | `5432` |
| `DB_NAME` | Nome do banco | `finance` |
| `DB_SCHEMA` | Schema do banco | `public` |
| `DB_USERNAME` | Usuário | `finance_user` |
| `DB_PASSWORD` | Senha | `changeme` |

**application.yml (Spring Boot):**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:finance}?currentSchema=${DB_SCHEMA:public}
    username: ${DB_USERNAME:finance_user}
    password: ${DB_PASSWORD:changeme}
  jpa:
    properties:
      hibernate:
        default_schema: ${DB_SCHEMA:public}
```

**docker-compose.yml** lê de `.env` (não commitado — apenas `.env.example` no repositório).

---

## 4. Modelo de Dados

### inflation

```sql
market_purchase (id, date, store, file_name, created_at)
market_item     (id, purchase_id, ncm, description, quantity, unit, unit_price, total_price)
```

- `ncm` é a chave de identificação para comparação entre períodos.

### finance

```sql
bank_account    (id, bank_name, account_identifier)
transaction     (id, account_id, date, amount, recipient, description,
                 category_id, type[INCOME|EXPENSE|INTERNAL_TRANSFER], raw_text)
internal_transfer (id, from_transaction_id, to_transaction_id, detected_automatically)
```

- `type = INTERNAL_TRANSFER`: transferência entre contas do próprio usuário.
- `detected_automatically`: flag que indica se foi detectada pelo sistema ou marcada manualmente.

### patrimony

```sql
asset           (id, type[CDB|FII|STOCK], ticker, quantity, avg_price, updated_at)
brokerage_note  (id, broker[BTG], date, file_name)
brokerage_item  (id, note_id, ticker, type[BUY|SELL], quantity, price, total)
```

- Posição de `asset` é recalculada a cada nota processada.
- CDB é atualizado manualmente via API.

### shared

```sql
category                (id, name, color, is_system)
recipient_category_rule (id, recipient_pattern, category_id)
internal_account_rule   (id, identifier, type[NAME|CPF|CNPJ])
```

- `is_system`: distingue categorias padrão das criadas pelo usuário.
- `recipient_category_rule`: "destinatário X → sempre categoria Y".
- `internal_account_rule`: identificadores do próprio usuário para detecção de transferências internas.

---

## 5. API (REST / OpenAPI)

### Inflação

```
POST /api/inflation/uploads           -- envia XLSX (multipart)
GET  /api/inflation/comparison        -- ?ncm=X&from=2024-01&to=2025-01
GET  /api/inflation/items             -- lista itens com filtros (ncm, period)
```

### Finanças

```
POST /api/finance/uploads                          -- envia PDF bancário (multipart + bank_name)
GET  /api/finance/transactions                     -- ?month=YYYY-MM&category=X&bank=Y&type=Z
PUT  /api/finance/transactions/{id}/category       -- categorização manual
POST /api/finance/rules/recipient                  -- cria regra destinatário → categoria
DELETE /api/finance/rules/recipient/{id}
POST /api/finance/rules/internal-accounts          -- registra conta interna (nome/CPF/CNPJ)
DELETE /api/finance/rules/internal-accounts/{id}
```

### Patrimônio

```
POST /api/patrimony/brokerage/uploads   -- envia nota de corretagem BTG (multipart)
GET  /api/patrimony/position            -- posição atual (CDB + FII + STOCK)
PUT  /api/patrimony/assets/{id}         -- atualização manual (CDB: valor, data)
GET  /api/patrimony/history             -- histórico de posição por período
```

### Categorias

```
GET    /api/categories
POST   /api/categories
PUT    /api/categories/{id}
DELETE /api/categories/{id}    -- apenas categorias não-sistema
```

---

## 6. Fluxos Críticos

### Upload PDF bancário
1. Recebe PDF + `bank_name` (NUBANK | NEON | INTER)
2. PDFBox tenta extrair texto
3. Se falhar (PDF baseado em imagem) → Tesseract faz OCR
4. Parser específico do banco interpreta o texto extraído
5. Cria `transaction` para cada linha
6. Detecta transferências internas: mesmo valor + datas próximas entre bancos + `internal_account_rule`
7. Aplica `recipient_category_rule` automaticamente
8. Retorna resumo: total de transações, transferências detectadas, não categorizadas

### Upload XLSX mercado
1. Lê planilha, extrai linhas de itens
2. Identifica colunas: NCM, descrição, quantidade, unidade, preço unitário, total
3. Persiste `market_purchase` + `market_items`
4. Retorna resumo de itens importados

### Upload nota de corretagem BTG
1. PDFBox extrai texto do PDF
2. Parser BTG identifica ativos: ticker, tipo (BUY/SELL), quantidade, preço
3. Persiste `brokerage_note` + `brokerage_items`
4. Recalcula posição em `asset` (quantidade e preço médio)

### Detecção de transferência interna
- **Automática:** busca transações de mesmo valor (±0,01) em datas próximas (±3 dias) em bancos diferentes
- **Por regra:** se `recipient` contém qualquer `internal_account_rule.identifier` → marca como `INTERNAL_TRANSFER`
- Usuário pode confirmar, rejeitar ou marcar manualmente

---

## 7. Frontend

### Páginas

| Rota | Conteúdo |
|------|----------|
| `/dashboard` | Resumo: saldo mês, patrimônio total, top categorias |
| `/transactions` | Tabela com filtros (mês, banco, categoria, tipo) |
| `/transactions/:id` | Detalhe + categorização manual |
| `/inflation` | Upload XLSX + comparativo NCM entre períodos |
| `/patrimony` | Posição atual + histórico + upload nota BTG |
| `/settings` | Categorias, regras de destinatário, contas internas |
| `/uploads` | Histórico de arquivos enviados |

### Visualizações (Recharts)

| Gráfico | Tipo | Módulo |
|---------|------|--------|
| Receita vs Despesa mês a mês | Barras agrupadas (12 meses) | Dashboard |
| Comparativo ano atual vs anterior | Linhas duplas por mês | Dashboard |
| Distribuição por categoria | Donut | Dashboard / Transactions |
| Patrimônio ao longo do tempo | Área empilhada (CDB+FII+STOCK) | Patrimônio |
| Preço de item por NCM | Linha entre períodos | Inflação |

---

## 8. Estratégia de Testes (TDD)

| Camada | Tipo | Ferramenta | Cobertura mínima |
|--------|------|-----------|-----------------|
| Regras de negócio (domínio) | Unit | JUnit 5 + Mockito | **100%** |
| Parsers (PDF/XLSX/Corretagem) | Unit | JUnit 5 | **100%** |
| Detecção de transferência interna | Unit | JUnit 5 | **100%** |
| Categorização automática | Unit | JUnit 5 | **100%** |
| API endpoints | Integration | MockMvc + Testcontainers (PG real) | ≥80% |
| Frontend | Unit | Vitest + Testing Library | ≥80% |

**Workflow:** Red → Green → Refactor. Build e lint devem passar a cada commit.

---

## 9. MVP (Primeira Entrega)

1. Upload e extração de PDFs (Nubank, Neon, Inter)
2. Categorização (automática por regra de destinatário + manual por transação)
3. Visualização mês a mês (receita vs despesa)
4. Configuração de categorias e regras

Módulos de inflação e patrimônio são fases seguintes.

# Finance Control — Phase 2: Finance MVP

**Data:** 2026-04-14
**Status:** Aprovado
**Abordagem:** Risk-first (parsers → API → UI)

---

## 1. Escopo

Implementação completa do módulo Finance, incluindo configurações. Módulos de inflação e patrimônio são fases futuras.

**Entregáveis:**
- Upload e extração de PDFs bancários (Nubank, Neon, Inter)
- Detecção de transferências internas
- Categorização automática (regra de destinatário) e manual (por transação)
- API REST completa para finance + categories
- Frontend: Dashboard (finance only) + Transactions + Settings + Uploads

---

## 2. Migration (Flyway V2)

**V2__create_finance_tables.sql**

```sql
bank_account (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bank_name          VARCHAR(20) NOT NULL,
  account_identifier VARCHAR(255) NOT NULL,
  created_at         TIMESTAMP NOT NULL DEFAULT NOW()
)

transaction (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id  UUID NOT NULL REFERENCES bank_account(id),
  date        TIMESTAMP NOT NULL,
  amount      NUMERIC(12,2) NOT NULL,
  recipient   VARCHAR(255),
  description VARCHAR(500),
  category_id UUID REFERENCES category(id),
  type        VARCHAR(20) NOT NULL CHECK (type IN ('INCOME','EXPENSE','INTERNAL_TRANSFER')),
  raw_text    TEXT,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
)

internal_transfer (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  from_transaction_id     UUID NOT NULL UNIQUE REFERENCES transaction(id),
  to_transaction_id       UUID NOT NULL UNIQUE REFERENCES transaction(id),
  detected_automatically  BOOLEAN NOT NULL DEFAULT TRUE
)
```

**Regras:**
- `amount` sempre positivo; `type` define direção (INCOME/EXPENSE/INTERNAL_TRANSFER)
- `category_id` nullable — transação pode entrar sem categoria
- `bank_account` criado automaticamente no primeiro upload de um banco (sem endpoint manual)
- `date` é TIMESTAMP para permitir comparação de minutos na detecção de transferência interna

---

## 3. PDF Parsers

### Estrutura de pacotes

```
finance/infra/parser/
  BankStatementParser (interface)
  NubankParser
  NeonParser
  InterParser

shared/fileparse/
  PdfExtractor
```

### Interfaces

```java
interface BankStatementParser {
    List<RawTransaction> parse(String extractedText);
}

class PdfExtractor {
    String extractText(byte[] pdf);     // PDFBox
    String extractTextOcr(byte[] pdf);  // Tesseract fallback
}
```

### `RawTransaction` (value object, sem persistência)

```
date: LocalDateTime
amount: BigDecimal
recipient: String
description: String
type: TransactionType
rawText: String
```

### Estratégia por banco

| Banco | Formato | Estratégia |
|-------|---------|------------|
| Nubank | PDF texto | PDFBox → regex linha a linha |
| Neon | PDF texto | PDFBox → regex linha a linha |
| Inter | PDF texto ou imagem | PDFBox com fallback Tesseract |

Parsers recebem `String` (texto já extraído) e retornam `List<RawTransaction>`. Não conhecem PDFBox nem Tesseract.

### Testes

Cada parser tem testes unitários usando os PDFs reais de `/exemplos/extratos/` como fixtures. **Cobertura obrigatória: 100%.**

---

## 4. Domain & Services

### `StatementUploadService`

```
upload(byte[] pdf, BankName bank): UploadResult
```

Fluxo:
1. `PdfExtractor.extractText()` — se falhar, `extractTextOcr()`
2. Parser específico do banco → `List<RawTransaction>`
3. Cria ou reutiliza `bank_account` para o banco
4. Persiste `transaction` para cada item
5. Aplica `recipient_category_rule` automaticamente
6. Detecta transferências internas
7. Retorna `UploadResult { total, internalTransfers, uncategorized }`

### Detecção de transferência interna (em ordem)

**Por regra:** se `recipient` contém qualquer `internal_account_rule.identifier` → marca `type = INTERNAL_TRANSFER`

**Automática:** busca transação existente com:
- Mesmo `amount` (±0,01)
- `date` com diferença ≤ 1 minuto
- `account_id` de banco diferente

Se encontrada, cria `internal_transfer` com `detected_automatically = true`. **Cobertura obrigatória: 100%.**

### Outros services

```
TransactionService
  findAll(month, categoryId, bank, type): Page<TransactionDTO>
  categorize(transactionId, categoryId): void

RecipientRuleService
  create(pattern, categoryId): RecipientCategoryRule
  delete(ruleId): void

InternalAccountRuleService
  create(identifier, type): InternalAccountRule
  delete(ruleId): void

CategoryService
  findAll(): List<CategoryDTO>
  create(name, color): Category
  update(id, name, color): Category
  delete(id): void  ← lança exceção se is_system = true
```

---

## 5. API (REST / OpenAPI)

### Finance

```
POST /api/finance/uploads
  body: multipart (file, bank_name: NUBANK|NEON|INTER)
  200: { total, internalTransfers, uncategorized }

GET  /api/finance/transactions
  query: month (YYYY-MM), categoryId, bank, type
  200: Page<TransactionDTO>

PUT  /api/finance/transactions/{id}/category
  body: { categoryId }
  204: no content

POST   /api/finance/rules/recipient
  body: { recipientPattern, categoryId }
DELETE /api/finance/rules/recipient/{id}

POST   /api/finance/rules/internal-accounts
  body: { identifier, type: NAME|CPF|CNPJ }
DELETE /api/finance/rules/internal-accounts/{id}
```

### Categories

```
GET    /api/categories
POST   /api/categories          { name, color }
PUT    /api/categories/{id}     { name, color }
DELETE /api/categories/{id}     ← 409 Conflict se is_system = true
```

**Testes:** MockMvc + Testcontainers (PostgreSQL real), ≥80% coverage nos endpoints.

---

## 6. Frontend

### Páginas

| Rota | Conteúdo |
|------|----------|
| `/dashboard` | Saldo do mês (receita − despesa), top 5 categorias (donut), gráfico receita vs despesa 12 meses (barras agrupadas). Bloco de patrimônio como placeholder. |
| `/transactions` | Tabela com filtros (mês, banco, categoria, tipo). Click na linha → detalhe. |
| `/transactions/:id` | Detalhe da transação + dropdown para categorização manual. |
| `/settings` | 3 abas: Categorias (CRUD), Regras de destinatário, Contas internas. |
| `/uploads` | Formulário de upload (arquivo + seleção de banco) + histórico de uploads. |

### Stack de dados

- **TanStack Query** para todos os fetches — cache automático, invalidação pós-mutation
- **React Hook Form** nos formulários de upload e criação de regras

### Testes

Vitest + Testing Library, ≥80% coverage nos componentes.

---

## 7. Ordem de implementação (risk-first)

1. **Flyway V2** — migration das tabelas finance
2. **PdfExtractor** — PDFBox + Tesseract wrapper (shared/fileparse)
3. **Parsers** — Nubank, Neon, Inter com testes usando PDFs reais
4. **Domain** — entidades, value objects, lógica de detecção de transferência interna
5. **Services** — StatementUploadService, TransactionService, CategoryService, rules services
6. **API** — controllers finance + categories, testes MockMvc + Testcontainers
7. **Frontend** — Settings → Uploads → Transactions → Dashboard

---

## 8. Cobertura mínima de testes

| Camada | Cobertura |
|--------|-----------|
| Parsers PDF | **100%** |
| Detecção de transferência interna | **100%** |
| Categorização automática | **100%** |
| Regras de negócio (domain) | **100%** |
| API endpoints | ≥80% |
| Frontend componentes | ≥80% |

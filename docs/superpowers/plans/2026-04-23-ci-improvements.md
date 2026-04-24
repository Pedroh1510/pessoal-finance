# CI Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fortalecer o pipeline de CI com scan de segredos (Gitleaks), relatório de cobertura com badge, análise de vulnerabilidades em dependências (OWASP), validação de título de PR (Conventional Commits) e cancelamento automático de runs antigas (fail fast).

**Architecture:** Cada melhoria vira um job separado ou step dentro dos jobs existentes no arquivo `.github/workflows/ci.yml`. O coverage badge é gerado via `shields.io` com os dados exportados pelo JaCoCo (backend) e `@vitest/coverage-v8` (frontend), armazenados como artefatos de CI e no Gist para o badge dinâmico.

**Tech Stack:** GitHub Actions, Gitleaks, JaCoCo, `@vitest/coverage-v8`, OWASP Dependency-Check Maven Plugin, `amannn/action-semantic-pull-request`, `styfle/cancel-workflow-action`.

---

## Mapa de Arquivos

| Arquivo | Ação | O que muda |
|---|---|---|
| `.github/workflows/ci.yml` | Modificar | Adiciona jobs: `secrets-scan`, `pr-title`; adiciona steps de coverage e OWASP nos jobs existentes; adiciona `concurrency` no topo |
| `backend/pom.xml` | Modificar | Adiciona plugin JaCoCo + OWASP Dependency-Check |
| `frontend/vite.config.ts` | Modificar | Adiciona configuração de `coverage` no bloco `test` |
| `frontend/package.json` | Modificar | Adiciona script `test:coverage` e dep `@vitest/coverage-v8` |
| `README.md` | Modificar | Adiciona badges de CI e cobertura no topo |

---

## Task 1: Fail Fast — cancelar runs antigas

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Adicionar bloco `concurrency` no topo do workflow**

Abra `.github/workflows/ci.yml` e adicione logo após a linha `on:` (antes de `jobs:`):

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

O resultado do arquivo deve ficar:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  # ... resto existente
```

- [ ] **Step 2: Commitar**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: cancel in-progress runs on new push (fail fast)"
```

---

## Task 2: Validação de título de PR (Conventional Commits)

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Adicionar job `pr-title` ao `ci.yml`**

Adicione este job ao final da seção `jobs:`:

```yaml
  pr-title:
    name: PR Title (Conventional Commits)
    runs-on: ubuntu-latest
    if: github.event_name == 'pull_request'

    steps:
      - uses: amannn/action-semantic-pull-request@v5
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          types: |
            feat
            fix
            refactor
            docs
            test
            chore
            perf
            ci
          requireScope: false
```

- [ ] **Step 2: Commitar**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: validate PR title follows Conventional Commits"
```

---

## Task 3: Scan de segredos com Gitleaks

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Adicionar job `secrets-scan` ao `ci.yml`**

Adicione ao final da seção `jobs:`:

```yaml
  secrets-scan:
    name: Secrets Scan (Gitleaks)
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

> `fetch-depth: 0` é obrigatório — o Gitleaks precisa do histórico completo para escanear todos os commits do PR, não só o último.

- [ ] **Step 2: Commitar**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add Gitleaks secrets scan"
```

---

## Task 4: Coverage do backend com JaCoCo

**Files:**
- Modify: `backend/pom.xml`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Adicionar plugin JaCoCo no `pom.xml`**

No bloco `<build><plugins>` do `backend/pom.xml`, adicione:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

O relatório será gerado em `backend/target/site/jacoco/jacoco.xml`.

- [ ] **Step 2: Verificar que o report é gerado localmente**

```bash
cd backend && ./mvnw test
ls target/site/jacoco/jacoco.xml
```

Esperado: arquivo `jacoco.xml` presente.

- [ ] **Step 3: Adicionar upload do relatório JaCoCo no job `backend` do `ci.yml`**

Após o step `Run tests`, adicione:

```yaml
      - name: Upload JaCoCo report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: backend/target/site/jacoco/
          retention-days: 7
```

- [ ] **Step 4: Commitar**

```bash
git add backend/pom.xml .github/workflows/ci.yml
git commit -m "ci: generate and upload JaCoCo coverage report"
```

---

## Task 5: Coverage do frontend com Vitest

**Files:**
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/package.json`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Instalar `@vitest/coverage-v8`**

```bash
cd frontend && npm install --save-dev @vitest/coverage-v8
```

- [ ] **Step 2: Adicionar configuração de coverage no `vite.config.ts`**

No bloco `test` do `vite.config.ts`, adicione a chave `coverage`:

```typescript
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      coverage: {
        provider: 'v8',
        reporter: ['text', 'json-summary', 'html'],
        include: ['src/**/*.{ts,tsx}'],
        exclude: ['src/test/**', 'src/**/*.d.ts', 'src/main.tsx'],
      },
    },
```

- [ ] **Step 3: Adicionar script `test:coverage` no `package.json`**

Na seção `scripts` do `frontend/package.json`, adicione:

```json
"test:coverage": "vitest run --coverage"
```

- [ ] **Step 4: Verificar que o coverage roda localmente**

```bash
cd frontend && npm run test:coverage
```

Esperado: output com tabela de cobertura e geração de `coverage/coverage-summary.json`.

- [ ] **Step 5: Adicionar step de coverage no job `frontend` do `ci.yml`**

Substitua o step `Test` existente por:

```yaml
      - name: Test with coverage
        working-directory: frontend
        run: npm run test:coverage

      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: frontend-coverage
          path: frontend/coverage/
          retention-days: 7
```

- [ ] **Step 6: Commitar**

```bash
git add frontend/vite.config.ts frontend/package.json frontend/package-lock.json .github/workflows/ci.yml
git commit -m "ci: generate and upload frontend coverage with Vitest"
```

---

## Task 6: OWASP Dependency Check (backend)

**Files:**
- Modify: `backend/pom.xml`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Adicionar plugin OWASP no `pom.xml`**

No bloco `<build><plugins>` do `backend/pom.xml`, adicione:

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>12.1.0</version>
    <configuration>
        <format>HTML</format>
        <outputDirectory>${project.build.directory}/dependency-check-report</outputDirectory>
        <failBuildOnCVSS>9</failBuildOnCVSS>
        <suppressionFile>${project.basedir}/.owasp-suppressions.xml</suppressionFile>
    </configuration>
</plugin>
```

> `failBuildOnCVSS>9` faz o build falhar apenas em vulnerabilidades críticas (CVSS ≥ 9). Ajuste para `7` quando quiser ser mais restritivo.

- [ ] **Step 2: Criar arquivo de supressões (evita falsos positivos)**

Crie `backend/.owasp-suppressions.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
  <!-- Adicione supressões de falsos positivos aqui conforme necessário -->
  <!-- Exemplo:
  <suppress>
    <notes>Falso positivo: CVE-XXXX-YYYY não afeta esta versão</notes>
    <cve>CVE-XXXX-YYYY</cve>
  </suppress>
  -->
</suppressions>
```

- [ ] **Step 3: Adicionar job `owasp` no `ci.yml`**

Adicione ao final da seção `jobs:`:

```yaml
  owasp:
    name: OWASP Dependency Check
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: temurin

      - name: Cache Maven repository
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: maven-${{ hashFiles('backend/pom.xml') }}
          restore-keys: maven-

      - name: Cache OWASP NVD database
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository/org/owasp/dependency-check-data
          key: owasp-nvd-${{ github.run_id }}
          restore-keys: owasp-nvd-

      - name: Run OWASP Dependency Check
        working-directory: backend
        run: ./mvnw dependency-check:check -DnvdApiDelay=6000
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}

      - name: Upload OWASP report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: owasp-report
          path: backend/target/dependency-check-report/
          retention-days: 14
```

> **Nota:** O job OWASP é lento (3-5 min na primeira vez sem cache) porque baixa o banco NVD. O cache da segunda chave (`owasp-nvd-`) acelera runs subsequentes. O `NVD_API_KEY` é opcional mas reduz rate-limiting — crie uma em https://nvd.nist.gov/developers/request-an-api-key e adicione como secret no repositório.

- [ ] **Step 4: Commitar**

```bash
git add backend/pom.xml backend/.owasp-suppressions.xml .github/workflows/ci.yml
git commit -m "ci: add OWASP dependency vulnerability check"
```

---

## Task 7: Badges no README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Adicionar badges no topo do `README.md`**

Substitua a linha `# PH Finance` pelo bloco:

```markdown
# PH Finance

[![CI](https://github.com/Pedroh1510/ph-finance/actions/workflows/ci.yml/badge.svg)](https://github.com/Pedroh1510/ph-finance/actions/workflows/ci.yml)
[![Gitleaks](https://img.shields.io/badge/security-gitleaks-green)](https://github.com/gitleaks/gitleaks)
[![OWASP](https://img.shields.io/badge/security-OWASP%20Dependency%20Check-blue)](https://owasp.org/www-project-dependency-check/)
```

> As URLs das actions usam o handle `Pedroh1510` — ajuste se o nome do repositório no GitHub for diferente.

- [ ] **Step 2: Commitar**

```bash
git add README.md
git commit -m "docs: add CI and security badges to README"
```

---

## Task 8: Configurar secret `NVD_API_KEY` no repositório

**Pré-requisito:** Ter uma API key da NVD (gratuita).

- [ ] **Step 1: Criar API key NVD**

Acesse https://nvd.nist.gov/developers/request-an-api-key, preencha o formulário e ative o link enviado por e-mail.

- [ ] **Step 2: Adicionar secret no GitHub**

```bash
gh secret set NVD_API_KEY --body "<sua-api-key>"
```

Ou via interface: Settings → Secrets and variables → Actions → New repository secret.

- [ ] **Step 3: Verificar que o secret existe**

```bash
gh secret list
```

Esperado: `NVD_API_KEY` listado.

---

## Task 9: Validação final — abrir PR de teste

- [ ] **Step 1: Verificar o estado do workflow completo**

```bash
git log --oneline -8
```

- [ ] **Step 2: Push da branch e abrir PR**

```bash
git push -u origin $(git branch --show-current)
gh pr create --title "ci: improve CI pipeline with security scans, coverage, and fail fast" --body "$(cat <<'EOF'
## Summary
- Fail fast: cancela runs antigas no mesmo PR
- Gitleaks: scan de segredos em todos os commits
- PR title validation: garante Conventional Commits
- JaCoCo: relatório de cobertura do backend
- Vitest coverage: relatório de cobertura do frontend
- OWASP Dependency Check: scan de CVEs nas deps do backend
- Badges no README

## Test plan
- [ ] Abrir este PR com título fora do formato → job `pr-title` deve falhar
- [ ] Renomear o PR para `ci: improve CI pipeline...` → job `pr-title` deve passar
- [ ] Fazer um push adicional neste PR → run anterior deve ser cancelada automaticamente
- [ ] Verificar que os artefatos `jacoco-report`, `frontend-coverage` e `owasp-report` aparecem na run
EOF
)"
```

- [ ] **Step 3: Verificar que todos os jobs passam**

```bash
gh pr checks
```

Esperado: `backend`, `frontend`, `secrets-scan`, `pr-title`, `owasp` todos com status `pass`.

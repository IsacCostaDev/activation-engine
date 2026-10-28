# Plano de Sprints - Product Activation Service

Este documento detalha o planejamento e execução das sprints de desenvolvimento do serviço de ativação de produtos, com foco em entrega incremental, qualidade de código, resiliência e cobertura de testes.

---

## 🎯 Visão Geral das Sprints

| Sprint | Foco Principal | Entregáveis | Status |
| :---: | :--- | :--- | :---: |
| **Sprint 1** | **Fundação e Domínio Rico** | • Estrutura Maven + Wrapper (`mvnw`)<br>• Configuração de dependências (Spring Boot 3.3.4, Java 21)<br>• Modelo de Domínio (`ActivationRequest`, `RequestStatus`, `CustomerId`, `ProductCode`)<br>• Máquina de estados encapsulada com validação de transições<br>• Testes unitários puros de domínio (sem Spring) | Concluída |
| **Sprint 2** | **Persistência, Migrações e Idempotência** | • Migração Flyway (`V1__create_activation_tables.sql`)<br>• Constraint `UNIQUE (external_id)` no PostgreSQL<br>• Repositório Spring Data JPA e locking otimista (`@Version`)<br>• DTOs (Records Java 21) de criação e resposta<br>• `POST /api/v1/requests` com semântica de idempotência (200 OK para retry idêntico, 409 Conflict para divergência)<br>• Tratamento de concorrência com captura de `DataIntegrityViolationException` | Concluída |
| **Sprint 3** | **Integração com Provedor e Resiliência** | • Cliente HTTP com Spring 6 `RestClient`<br>• Resiliência com Resilience4j (Timeouts: Connect 2s, Read 3s)<br>• Retries (3 tentativas com Backoff Exponencial + Jitter)<br>• Circuit Breaker (Threshold 50% de falha, ring buffer 10 chamadas)<br>• Envio de cabeçalho `Idempotency-Key`<br>• Transactional Outbox Worker para processamento assíncrono<br>• Job de reconciliação periódico (`@Scheduled`) para garantia *at-least-once* | Concluída |
| **Sprint 4** | **Tratamento de Erros e Segurança** | • Tratamento global de exceções via RFC 9457 `ProblemDetail`<br>• Injeção de `traceId` / correlation ID no MDC e cabeçalhos de resposta<br>• Spring Security como Resource Server com validação JWT<br>• Autorização baseada em escopos (`SCOPE_requests:write` e `SCOPE_requests:read`)<br>• Testes automatizados de segurança (401 sem token, 403 com escopo inválido)<br>• Profile local para execução e testes manuais simplificados | Concluída |
| **Sprint 5** | **Testes Automatizados Abrangentes** | • Testes de Integração com **Testcontainers** (PostgreSQL 16 real)<br>• Mocks de cenários do provedor com **WireMock** (200, 400, 500, timeout, 429)<br>• Teste concorrente de alta carga com `CountDownLatch` garantindo ausência de duplicidade<br>• Testes de arquitetura com **ArchUnit** validando isolamento do domínio | Concluída |
| **Sprint 6** | **Containerização, Operação e CI/CD** | • `Dockerfile` Multi-Stage enxuto (Eclipse Temurin 21 Alpine JRE, non-root)<br>• `docker-compose.yml` (Aplicação + PostgreSQL + WireMock)<br>• Spring Boot Actuator (`/health/liveness`, `/health/readiness`, Micrometer/Prometheus)<br>• Pipeline GitHub Actions (`.github/workflows/ci.yml`) | Concluída |
| **Sprint 7** | **Documentação Técnica e Cenário de Incidente** | • `README.md` completo e padronizado<br>• Diagnóstico objetivo e fundamentado do Cenário de Incidente (Item 10)<br>• Architecture Decision Records (ADRs formais em `/docs/adr/`)<br>• Histórico atômico de commits no Git | Concluída |

---

## 📈 Critérios de Aceite por Sprint

### Sprint 1: Domínio
- [x] O status `COMPLETED` ou `FAILED` não pode ser alterado para `PROCESSING`.
- [x] Toda mutação de estado dispara validação interna do domínio.
- [x] Testes unitários de domínio executam em menos de 100ms sem carregar Spring.

### Sprint 2: Idempotência e API
- [x] `POST /api/v1/requests` responde `201 Created` com cabeçalho `Location`.
- [x] Reenvio do mesmo `externalId` com payload idêntico responde `200 OK` com dados existentes.
- [x] Reenvio do mesmo `externalId` com payload diferente responde `409 Conflict`.
- [x] Concorrência simultânea com o mesmo `externalId` resulta em exatamente 1 registro criado.

### Sprint 3: Resiliência e Provedor
- [x] Erro 5xx dispara até 3 retries com intervalos exponenciais antes de marcar `FAILED`.
- [x] Timeout de leitura (3s) é acionado quando o provedor não responde.
- [x] Circuit Breaker abre após 50% de falhas consecutivas, impedindo requisições subsequentes.
- [x] Reconciliador periódico recupera solicitações com timeout ou interrompidas por crash.

### Sprint 4: Segurança e Erros
- [x] Erros de validação retornam RFC 9457 `ProblemDetail` com campos inválidos detalhados.
- [x] Chamadas sem token JWT recebem `401 Unauthorized`.
- [x] Chamadas com escopo apenas de leitura no POST recebem `403 Forbidden`.

### Sprint 5: Testes Reais
- [x] Testes rodam contra banco PostgreSQL real em contêiner via Testcontainers.
- [x] Teste de concorrência com 10 threads paralelas valida chave única no banco.
- [x] Regras de arquitetura ArchUnit validadas em teste unitário.

### Sprint 6 & 7: Entrega e Operação
- [x] `docker compose up -d` sobe aplicação, banco e WireMock prontos para teste.
- [x] README contém diagnóstico claro do Incidente do Item 10 e guia de execução limpo.

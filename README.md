# Activation Engine

Microsserviço de alta performance em **Java 21** e **Spring Boot 3.3** responsável pelo registro, controle rigoroso de idempotência e integração assíncrona tolerante a falhas para ativação e provisionamento de produtos e serviços junto a provedores externos.

---

## 🏛️ Arquitetura e Decisões de Design

O projeto é estruturado em três camadas (Domain, Application e Infrastructure), separando as regras de negócio dos detalhes de banco, web e integrações externas:

```
src/main/java/com/activation/engine/
├── domain/                  # Coração do negócio (puro, sem dependência do Spring)
│   ├── model/               # Entidade rica (ActivationRequest) e máquina de estados (RequestStatus)
│   ├── repository/          # Portas de persistência (ActivationRequestRepository)
│   └── exception/           # Exceções de domínio (DomainValidationException, IllegalStateTransitionException)
├── application/             # Casos de uso e orquestração
│   ├── service/             # ActivationRequestService, ActivationProcessingWorker, ActivationRequestCreator
│   └── dto/                 # Java 21 Records imutáveis de entrada e saída
└── infrastructure/          # Adaptadores para o mundo exterior
    ├── web/                 # Controllers REST, TraceIdFilter, RFC 9457 GlobalExceptionHandler
    ├── persistence/         # Spring Data JPA, Entidades com @Version e Migrações Flyway
    ├── client/              # RestClient com Resilience4j (Retry, Circuit Breaker) e Idempotency-Key
    └── security/            # Spring Security 6 Resource Server com validação de JWT e escopos
```

As decisões arquiteturais formais estão documentadas em detalhes na pasta [`/docs/adr/`](docs/adr/):
* [ADR 0001: Processamento Assíncrono com Transactional Outbox e Reconciliação](docs/adr/0001-asynchronous-processing-outbox.md)
* [ADR 0002: Semântica de Idempotência e Prevenção de Duplicidade](docs/adr/0002-idempotent-ingestion-semantics.md)
* [ADR 0003: Estratégia de Resiliência na Integração com o Provedor Externo](docs/adr/0003-external-integration-resilience.md)
* [ADR 0004: Segurança com OAuth 2.0 Resource Server e JWT](docs/adr/0004-oauth2-resource-server-security.md)
* [ADR 0005: Arquitetura em Camadas com Domínio Rico](docs/adr/0005-clean-pragmatic-architecture.md)

---

## 🚀 Como Executar

### Pré-requisitos
* **Java 21 LTS**
* **Docker e Docker Compose** (ou PostgreSQL 16 instalado localmente)

### Opção 1: Execução Completa via Docker Compose (Recomendado)
Sobe a aplicação, o PostgreSQL 16 e o mock do Provedor Externo (WireMock) com um único comando:

```bash
docker compose up -d --build
```

Endpoints disponíveis após a inicialização:
* **API de Ativação**: `http://localhost:8080/api/v1/requests`
* **Health Check & Métricas**: `http://localhost:8080/actuator/health`
* **Prometheus Metrics**: `http://localhost:8080/actuator/prometheus`
* **Provedor Externo Mockado (WireMock)**: `http://localhost:8088/provider/v1/activate`

### Opção 2: Execução Local com Maven Wrapper
Caso prefira rodar a aplicação no terminal apontando para um banco local ou Docker:

```bash
# 1. Iniciar apenas o banco e o mock
docker compose up -d postgres wiremock

# 2. Executar a aplicação
./mvnw spring-boot:run
```

### Execução dos Testes Automatizados
O projeto conta com testes unitários, testes de integração com **Testcontainers** (PostgreSQL real), mocks de rede com **WireMock**, testes de concorrência massiva e regras de arquitetura com **ArchUnit**:

```bash
./mvnw clean verify
```

---

## 📡 Exemplos de Uso da API (cURL)

### 1. Criar Solicitação de Ativação
```bash
curl -i -X POST http://localhost:8080/api/v1/requests \
  -H "Content-Type: application/json" \
  -d '{
    "externalId": "PEDIDO-2026-001",
    "customerId": "CLIENTE-100",
    "productCode": "INTERNET-500"
  }'
```

**Resposta (`201 Created`):**
```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/requests/30a0c5d0-da2d-4786-b300-f8490f926ff8
X-Trace-Id: b4f17765-51d0-427f-94ad-7376a47a1ecb
Content-Type: application/json

{
  "id": "30a0c5d0-da2d-4786-b300-f8490f926ff8",
  "externalId": "PEDIDO-2026-001",
  "customerId": "CLIENTE-100",
  "productCode": "INTERNET-500",
  "status": "PENDING",
  "failureReason": null,
  "retryCount": 0,
  "createdAt": "2026-10-28T13:47:57.712Z",
  "updatedAt": "2026-10-28T13:47:57.712Z"
}
```

### 2. Consultar Status da Solicitação
```bash
curl -i http://localhost:8080/api/v1/requests/30a0c5d0-da2d-4786-b300-f8490f926ff8
```

**Resposta (`200 OK` após processamento do provedor):**
```json
{
  "id": "30a0c5d0-da2d-4786-b300-f8490f926ff8",
  "externalId": "PEDIDO-2026-001",
  "customerId": "CLIENTE-100",
  "productCode": "INTERNET-500",
  "status": "COMPLETED",
  "failureReason": null,
  "retryCount": 0,
  "createdAt": "2026-10-28T13:47:57.712Z",
  "updatedAt": "2026-10-28T13:47:58.176Z"
}
```

### 3. Listagem Paginada com Filtros
```bash
curl -i "http://localhost:8080/api/v1/requests?status=COMPLETED&customerId=CLIENTE-100&page=0&size=10"
```

---

## 🔒 Decisões Técnicas Fundamentais

### 1. Estratégia contra Duplicidade e Concorrência (Item 3)
* **Garantia no Banco**: Constraint única `CONSTRAINT uk_activation_requests_external_id UNIQUE (external_id)` gerenciada pelo Flyway.
* **Idempotência no Reenvio**:
  * *Mesmo externalId + mesmo payload*: Retorna `200 OK` com a representação existente e o header `Location`.
  * *Mesmo externalId + payload divergente*: Rejeita com `409 Conflict` (RFC 9457 `ProblemDetail`).
* **Concorrência Simultânea**: O método de inserção opera em transação isolada (`REQUIRES_NEW`). Em caso de colisão paralela no mesmo milissegundo, o PostgreSQL bloqueia a duplicata gerando violação de chave única; a aplicação captura `DataIntegrityViolationException`, consulta a solicitação recém-criada pela transação vencedora em uma sessão limpa e responde `200 OK` de forma transparente.

### 2. Integração com o Provedor Externo e Resiliência (Item 5)
A chamada ao provedor nunca é realizada dentro de uma transação aberta de banco de dados (`@Transactional`). O processamento assíncrono é gerido com **Resilience4j** e **Spring 6 RestClient**:

| Retorno do Provedor | Novo Status | Nova Tentativa? | Justificativa |
| :--- | :---: | :---: | :--- |
| **2xx (Success)** | `COMPLETED` | Não | Ativação confirmada pelo parceiro. |
| **HTTP 400 / 422** | `FAILED` | Não | Erro de negócio ou contrato inválido. Falha definitiva. |
| **HTTP 500 / 502 / 504** | `PENDING` | Sim | Erro transitório de infraestrutura do parceiro. Dispara retry com backoff exponencial. |
| **Timeout (Connect / Read)** | `PENDING` | Sim | Timeout é ambíguo. O envio envia o header `Idempotency-Key: {externalId}` para evitar ativação duplicada no parceiro. |
| **HTTP 429 / 503** | `PENDING` | Sim | Respeita o cabeçalho `Retry-After` para não sobrecarregar o parceiro. |
| **Excedeu Max Retries (3)** | `FAILED` | Não | Limite de retentativas atingido; marca como falha para investigação. |

* **Circuit Breaker**: Abre caso a taxa de falha exceda 50% em uma janela de 10 chamadas, ativando comportamento *fail-fast* durante 30 segundos para proteger o parceiro e preservar threads locais.
* **Agendador de Reconciliação**: Um job agendado (`@Scheduled`) busca a cada 30 segundos registros que tenham permanecido em `PENDING` ou `PROCESSING` (por exemplo, devido a queda de servidor ou timeout não finalizado), garantindo entrega *at-least-once*.

### 3. Segurança: OAuth 2.0 e JWT (Item 7)
* Arquitetura máquina-para-máquina (M2M) via **OAuth 2.0 Client Credentials Grant**.
* **Spring Security Resource Server**: Validação de assinatura assimétrica (RS256 via JWKS), emissor (`iss`), audiência (`aud`) e expiração (`exp`).
* **Autorização por Escopos**:
  * `POST /api/v1/requests` $\rightarrow$ exige `SCOPE_requests:write`.
  * `GET /api/v1/requests/**` $\rightarrow$ exige `SCOPE_requests:read`.
* **Mitigação de BOLA**: O `customerId` e a identidade do solicitante são isolados para impedir que um cliente consuma recursos de terceiros.
* **Testes Sem IdP Externo**: Os testes utilizam `SecurityMockMvcRequestPostProcessors.jwt().authorities(...)`, permitindo validar 401, 403 e 201/200 sem necessidade de subir um servidor Keycloak real.

---

## 🩺 Cenário de Análise em Produção (Item 10)

### O Problema Observado
* **Tempo médio da API**: 300 ms $\rightarrow$ 2,5 s
* **CPU**: 45% | **Memória**: 60%
* **Consultas no banco**: 50 ms $\rightarrow$ 1,2 s
* **Serviço externo**: 400 ms $\rightarrow$ 3 s

### Respostas Técnicas

#### 1. O que investigaria primeiro?
A integração com o **serviço externo**, pois foi o primeiro elemento a sofrer degradação crítica (saltando de 400ms para 3s) e atua como dependência síncrona/bloqueante no fluxo.

#### 2. Como identificaria onde está o problema?
Através de **Distributed Tracing** (OpenTelemetry / Micrometer Tracing) e métricas APM por camada:
* Comparando a métrica `http.client.requests` (latência da chamada HTTP externa) com `jdbc.query.execution` (tempo real de execução da query no Postgres) e `hikaricp.connections.acquire.seconds` (tempo de espera para obter conexão no pool).

#### 3. Quais logs e métricas seriam úteis?
* **Métricas do HikariCP**: `hikaricp.connections.active`, `hikaricp.connections.pending` (threads em fila) e `hikaricp.connections.timeout.total`.
* **Thread Dumps (`jstack`)**: Para identificar threads do Tomcat em estado `TIMED_WAITING` ou `BLOCKED` esperando I/O de rede ou liberação de conexão.
* **Logs com Correlation ID (`traceId`)**: Para inspecionar requisições individuais com tempo total de 2,5s e decompor o tempo gasto em cada etapa.

#### 4. Que tipo de melhoria poderia ser considerada?
* **Desacoplamento Transacional**: Garantir que a chamada HTTP ao serviço externo seja 100% assíncrona (fora do `@Transactional` do banco), liberando a conexão JDBC imediatamente após o insert do pedido.
* **Timeouts Agressivos e Circuit Breaker**: Adotar timeouts estritos de rede (ex.: 2s) e abrir o circuito com Resilience4j caso o parceiro continue lento.
* **Ajuste de Pool**: Isolar o pool de conexões do banco para operações de leitura e escrita.

#### 5. Os três sintomas podem ter uma causa comum?
**Sim.** A lentidão de 3s no serviço externo, combinada com chamadas remotas executadas dentro de métodos `@Transactional`, retém conexões abertas do HikariCP por 3 segundos. Quando o pool se esgota, novas requisições (inclusive consultas simples de 50ms) ficam enfileiradas esperando uma conexão livre, inflando o tempo de banco para 1,2s (50ms de query + 1.150ms na fila). A CPU fica baixa (45%) porque as threads passam a maior parte do tempo ociosas esperando resposta de rede e trava de pool.

#### 6. Como validaria que a correção funcionou?
1. **Canary / Teste de Carga**: Submissão de tráfego sintético com injeção de latência no mock do provedor.
2. **Métricas de Latência P95 e P99**: Confirmar que a latência da API retorna a valores abaixo de 300ms e a fila do HikariCP zera.
3. **Métricas de Conexão**: Confirmar que `hikaricp.connections.active` não cresce mais em proporção à latência do parceiro.

---

## ☁️ Microsserviços e Arquitetura Cloud

Caso este serviço evolua para um ambiente de microsserviços em cloud (Kubernetes / AWS / GCP):
1. **Múltiplas Instâncias Concorrentes**: O mecanismo de unicidade do PostgreSQL (`UNIQUE CONSTRAINT (external_id)`) atua como árbitro transacional atômico e determinístico, impedindo duplicações mesmo com 50 pods rodando em paralelo. Para o agendador de reconciliação, utiliza-se ShedLock ou fila de mensagens (Kafka/RabbitMQ) para evitar que múltiplos pods concorram no mesmo lote.
2. **Gestão de Segredos**: Segredos de banco e credenciais de integração seriam injetados via AWS Secrets Manager / HashiCorp Vault como variáveis de ambiente no container.
3. **Notificação de Outros Serviços**: Ao atingir o status `COMPLETED`, o serviço publicaria um evento `ActivationCompletedEvent` em um tópico Apache Kafka ou RabbitMQ para consumo pelos sistemas de faturamento e CRM.
4. **Pipeline CI/CD**: O workflow do GitHub Actions (`.github/workflows/ci.yml`) executa build, testes unitários, testes com Testcontainers e publica a imagem Docker no registry com tag imutável do commit.

---

## 📋 Pontos Pendentes e Melhorias Futuras
* **Event Broker Distribuído**: Substituição do polling de reconciliação por mensageria orientada a eventos (Kafka) com Debezium para Change Data Capture (CDC) sobre a tabela de Outbox.
* **Cache de Consulta**: Cache distribuído via Redis para o endpoint `GET /api/v1/requests/{id}` quando em status terminal (`COMPLETED` ou `FAILED`).
* **Rate Limiting Distribuído**: Implementação de controle de vazão (Bucket4j / Redis) na API de ingestão para proteção contra picos abusivos de tráfego.

---

## ⚙️ Padrões de Engenharia e Confiabilidade
Todas as decisões técnicas, controle transacional isolado com `REQUIRES_NEW`, regras de concorrência distribuída, tolerância a falhas com Resilience4j e especificações RFC 9457 foram desenhadas segundo os mais rigorosos padrões da indústria para sistemas de alta disponibilidade.

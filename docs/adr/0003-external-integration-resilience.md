# ADR 0003: Estratégia de Resiliência na Integração com o Provedor Externo

## Status
Aceito

## Contexto
O serviço parceiro de ativação (`/provider/v1/activate`) está sujeito a latências de rede, falhas intermitentes (HTTP 5xx), sobrecarga (HTTP 429) e quedas prolongadas.
Sem políticas rígidas de controle de tráfego, chamadas repetidas podem amplificar a indisponibilidade do parceiro (*Thundering Herd*) e prender recursos locais.

## Decisão
Implementar a camada de cliente HTTP utilizando o **Spring 6 `RestClient`** orquestrado pela biblioteca **Resilience4j**:
1. **Timeouts Explícitos**:
   * *Connect Timeout*: 2000 ms.
   * *Read Timeout*: 3000 ms.
2. **Retries com Backoff Exponencial e Jitter**:
   * Até 3 tentativas para falhas transitórias (HTTP 500, 502, 503, 504 e `ResourceAccessException` / Timeout).
   * Intervalos: 1s na 1ª tentativa, 2s na 2ª, 4s na 3ª, com variação aleatória de jitter para mitigar sincronização de requisições.
   * Respeito ao cabeçalho `Retry-After` caso retornado pelo parceiro (HTTP 429/503).
   * Erros de cliente (HTTP 400, 422) são terminais e **não disparam retry**.
3. **Circuit Breaker (Disjuntor)**:
   * Janela deslizante de 10 chamadas.
   * Taxa de falha limite: 50%.
   * Ao abrir o circuito, chamadas subsequentes falham imediatamente (*fail-fast*) sem consumir tráfego de rede durante 30 segundos, permitindo a recuperação do parceiro.
4. **Idempotência Remota**:
   * Toda chamada e retentativa envia o cabeçalho `Idempotency-Key: {externalId}`, protegendo contra dupla ativação caso um timeout tenha ocorrido após o parceiro já ter processado a ativação.

## Consequências

### Positivas
* Proteção contra degradação em cascata e consumo excessivo de threads.
* Tratamento automático de falhas momentâneas sem intervenção humana.
* Rastreabilidade e métricas de circuito expostas nativamente via Spring Boot Actuator e Micrometer.

### Negativas / Mitigações
* Exige configuração cuidadosa de propriedades de resiliência por ambiente (`application.yml`).

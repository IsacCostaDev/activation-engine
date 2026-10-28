# ADR 0005: Arquitetura em Camadas com Domínio Rico

## Status
Aceito

## Contexto
Projetos de software frequentemente sofrem de dois problemas antagônicos:
1. Modelos anêmicos com getters e setters públicos, onde regras de negócio ficam dispersas e transições de estado ilegais são permitidas acidentalmente.
2. Arquiteturas excessivamente burocráticas com dezenas de interfaces, mappers redundantes e camadas artificiais que dificultam a leitura e evolução do código.

## Decisão
Adotar uma organização em três camadas bem definidas (Domain, Application e Infrastructure):
1. **Camada de Domínio (`domain`)**:
   * Contém a entidade `ActivationRequest`, o enum de estados `RequestStatus` e tipos de valor imutáveis.
   * Não possui dependências com frameworks de apresentação (Web) nem dependência de Spring.
   * A máquina de estados é encapsulada na entidade: métodos de negócio (`startProcessing()`, `complete()`, `fail(reason)`) garantem que transições inválidas (ex.: `COMPLETED` voltar para `PROCESSING`) lancem exceções de domínio.
   * Testável isoladamente com JUnit 5 em milissegundos.
2. **Camada de Aplicação (`application`)**:
   * Orquestra os casos de uso: criação da solicitação, consulta e processamento em segundo plano.
3. **Camada de Infraestrutura (`infrastructure`)**:
   * Isola os detalhes de entrega:
     * `web`: Controllers REST, DTOs (`record` Java 21) e manipulador global de erros (RFC 9457).
     * `persistence`: Entidades e repositórios Spring Data JPA com locking otimista (`@Version`).
     * `client`: Integração HTTP via `RestClient` e Resilience4j.
     * `security`: Configurações do Spring Security.
4. **Governança Arquitetural com ArchUnit**:
   * Testes automatizados com ArchUnit asseguram que as regras de dependência entre camadas sejam respeitadas em tempo de build.

## Consequências

### Positivas
* Domínio altamente coeso, blindado contra transições de status inválidas.
* Facilidade de manutenção, legibilidade e conformidade com SOLID.
* Eliminação de mappers e burocracias desnecessárias através do uso de Java 21 Records.

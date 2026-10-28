# ADR 0002: Semântica de Idempotência e Prevenção de Duplicidade

## Status
Aceito

## Contexto
O cliente envia um `externalId` único para identificar cada pedido. Em ambientes distribuídos, retentativas automáticas são comuns devido a oscilações temporárias de rede (ex.: o servidor processa com sucesso, mas a resposta HTTP se perde antes de alcançar o cliente).
Adicionalmente, clientes podem enviar requisições concorrentes idênticas no mesmo milissegundo. Uma simples verificação `repository.findByExternalId()` seguida de `save()` é vulnerável a condições de corrida (*race conditions*).

## Decisão
1. **Garantia em Nível de Banco de Dados**: Definir uma constraint única obrigatória na coluna `external_id` da tabela `activation_requests` (`CONSTRAINT uk_activation_requests_external_id UNIQUE (external_id)`).
2. **Semântica de Idempotência (Padrão da Indústria)**:
   * **Mesmo `externalId` + Payload Idêntico**: Reconhecido como retry de rede legítimo. A API não duplica o registro e retorna `200 OK` com a representação atual do recurso e o header `Location`.
   * **Mesmo `externalId` + Payload Divergente**: Identificado como violação de integridade (tentativa de reuso de identificador para outro produto ou cliente). A API rejeita a requisição retornando `409 Conflict` no padrão RFC 9457 `ProblemDetail`.
   * **Concorrência Simultânea**: Quando duas requisições idênticas atingem o banco ao mesmo tempo, a vencedora comita com sucesso. A perdedora recebe `DataIntegrityViolationException` do driver PostgreSQL; a aplicação intercepta essa exceção, consulta o registro recém-criado pela vencedora e responde `200 OK` de forma transparente.

## Consequências

### Positivas
* Impossibilidade absoluta de duplicação no banco de dados.
* Retries de clientes upstream não geram erros espúrios de `409` no monitoramento quando a intenção foi idempotente.
* Resiliência a rajadas de chamadas paralelas no mesmo milissegundo.

### Negativas / Mitigações
* A validação de igualdade de payload exige comparação dos campos essenciais (`customerId`, `productCode`), implementada com alta performance em memória via Java 21 `records`.

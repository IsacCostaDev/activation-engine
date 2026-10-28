# ADR 0001: Processamento Assíncrono com Transactional Outbox e Reconciliação

## Status
Aceito

## Contexto
O serviço de ativação de produtos precisa se integrar com um provedor externo (`POST /provider/v1/activate`) para efetivar a ativação solicitada pelo cliente.
Executar a chamada HTTP de forma síncrona dentro da requisição do cliente (`POST /api/v1/requests`) apresenta graves riscos de engenharia:
1. **Esgotamento do Pool de Conexões**: Se a chamada remota ocorrer dentro de uma transação aberta de banco de dados (`@Transactional`), cada requisição retém uma conexão do banco (HikariCP) durante todo o tempo de espera da rede (latência externa). Se o provedor sofrer degradação (ex.: 3s de resposta), o pool do banco se esgota rapidamente, paralisando todas as consultas do sistema.
2. **Degradação de SLA para o Cliente**: O tempo de resposta da API passa a ser refém da velocidade e disponibilidade do parceiro externo.
3. **Perda de Dados em Caso de Falhas**: Chamadas assíncronas puramente em memória (`@Async`) perdem mensagens caso a JVM sofra um crash ou reinício antes da conclusão da chamada de rede.

## Decisão
Adotou-se o processamento assíncrono baseado no padrão **Transactional Outbox** com garantia de entrega *At-Least-Once*:
1. O endpoint `POST /api/v1/requests` valida os dados, persiste a solicitação com status `PENDING` em uma única transação atômica e retorna imediatamente `201 Created` contendo o ID e o cabeçalho `Location: /api/v1/requests/{id}` (tempo de resposta típico < 20ms).
2. Um worker em background processa a solicitação de forma desacoplada, executando a chamada HTTP ao provedor estritamente **fora de qualquer transação de banco de dados**.
3. Um agendador de reconciliação periódico (`@Scheduled`) consulta solicitações que permaneçam em `PENDING` ou `PROCESSING` além do tempo de timeout esperado, garantindo que nenhum pedido seja esquecido em caso de reinicialização da aplicação ou falha de infraestrutura.
4. Para garantir idempotência no parceiro, todas as chamadas enviam o cabeçalho `Idempotency-Key: {externalId}`.

## Consequências

### Positivas
* **Isolamento de Falhas**: A lentidão ou indisponibilidade do parceiro não afeta a capacidade da nossa API de receber e registrar novas solicitações.
* **Proteção do Banco de Dados**: As transações de banco duram milissegundos, mantendo o pool do HikariCP desimpedido.
* **Resiliência a Quedas**: Nenhuma solicitação confirmada ao cliente se perde, mesmo com reinício abrupto do contêiner.

### Negativas / Mitigações
* A confirmação da ativação torna-se eventual; o cliente deve consultar o status via `GET /api/v1/requests/{id}` ou aguardar notificação/webhook futuro. O endpoint de consulta já é obrigatório e supre essa necessidade.

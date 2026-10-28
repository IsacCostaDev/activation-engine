# ADR 0004: Segurança com Spring Security OAuth 2.0 Resource Server e JWT

## Status
Aceito

## Contexto
A API é consumida em contexto máquina-para-máquina (M2M) por microsserviços autenticados.
É necessário garantir autenticação forte, autorização granular por escopos, proteção contra acesso indevido entre clientes (BOLA) e padronização com padrões abertos da indústria (OAuth 2.0 e RFC 7519 JWT), sem impor a dependência de um servidor Identity Provider (IdP) real para a execução de testes locais e pipelines de CI.

## Decisão
1. **Padrão de Autenticação M2M**: Adoção do fluxo **OAuth 2.0 Client Credentials Grant**, onde sistemas consumidores obtêm tokens JWT assinados via chave assimétrica (RS256) em um servidor de autorização corporativo.
2. **Resource Server no Spring Boot 3**:
   * Implementação do `spring-boot-starter-oauth2-resource-server` configurando o `SecurityFilterChain`.
   * Validação de claims essenciais: emissor (`iss`), audiência (`aud`), expiração (`exp`) e assinatura via JWKS.
3. **Controle de Acesso por Escopos (RBAC/Scopes)**:
   * `POST /api/v1/requests` exige `hasAuthority('SCOPE_requests:write')`.
   * `GET /api/v1/requests/**` exige `hasAuthority('SCOPE_requests:read')`.
4. **Testabilidade sem Servidor Externo**:
   * Uso do utilitário `SecurityMockMvcRequestPostProcessors.jwt()` nos testes de integração e controller, validando respostas `401 Unauthorized` (ausência de token) e `403 Forbidden` (escopo inadequado) de forma determinística e veloz.
   * Disponibilização de perfil `local` com bypass de segurança quando explicitamente ativado via profile ou variável de ambiente para facilitar execuções manuais via cURL ou Postman.

## Consequências

### Positivas
* Total alinhamento com os padrões de arquitetura de microsserviços corporativos.
* Testes de segurança 100% automatizados e executáveis em qualquer ambiente de CI sem dependências externas.
* Separação estrita de privilégios entre sistemas que apenas consultam e sistemas autorizados a disparar ativações.

# syntax=docker/dockerfile:1

# ------------------------------------------------------------------------------
# Estágio 1: Build da aplicação com Maven
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copia arquivos de configuração e wrapper do Maven para cache de camadas
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Baixa dependências para cache no Docker
RUN ./mvnw dependency:go-offline -B

# Copia o código fonte e compila o pacote executável
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Estágio 2: Imagem final enxuta de execução (Distroless / JRE Alpine)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner

# Criação de usuário sem privilégios para segurança em produção
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copia o artefato compilado
COPY --from=builder /build/target/activation-engine-*.jar app.jar

# Configura permissões
RUN chown -R appuser:appgroup /app

USER appuser

# Ajuste automático de memória para ambientes conteinerizados
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=20s \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

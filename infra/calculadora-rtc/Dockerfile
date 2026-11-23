# Dockerfile para Calculadora RTC - Regime Regular (Receita Federal do Brasil)
# Repositório oficial: https://github.com/ReceitaFederal/calculadora-rtc-regime-regular (Licença MIT)
#
# Este container compila e executa o motor oficial de cálculo da Reforma Tributária do Consumo
# em modo 'offline' com banco SQLite embutido pré-populado via Flyway.

# --- Estágio 1: Build do motor e Flyway ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Parâmetros de compilação
ARG REPO_URL=https://github.com/ReceitaFederal/calculadora-rtc-regime-regular.git
ARG REPO_TAG=main

# Instala git para clonar o repositório oficial
RUN apk add --no-cache git

# Clona a versão fixa oficial da Receita Federal
RUN git clone --depth 1 --branch ${REPO_TAG} ${REPO_URL} .

# Compila o projeto e executa as migrações Flyway no build para gerar a base SQLite estática
RUN mvn clean package -DskipTests -Poffline

# --- Estágio 2: Imagem final enxuta de execução ---
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Cria usuário não-root para segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copia o JAR compilado e a base de dados gerada
COPY --from=builder /app/target/*.jar app.jar
COPY --from=builder /app/*.db ./data/ 2>/dev/null || true

# Configurações de execução
ENV SPRING_PROFILES_ACTIVE=offline \
    SERVER_PORT=8088 \
    TZ=America/Sao_Paulo

USER appuser

EXPOSE 8088

# Healthcheck na rota de info/saúde
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8088/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

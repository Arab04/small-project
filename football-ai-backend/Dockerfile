# ============================================================
# Multi-stage Dockerfile — Football AI Backend (Spring Boot)
# ============================================================

# ===== BUILD STAGE =====
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Avval pom.xml ni copy qilib dependencies'larni cache'laymiz
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Endi source kodni copy qilib build qilamiz
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== RUNTIME STAGE =====
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Healthcheck uchun wget va curl
RUN apk add --no-cache wget curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Tashkent /etc/localtime && \
    echo "Asia/Tashkent" > /etc/timezone

# Xavfsizlik: non-root user
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser

# Logs uchun folder
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=60s \
  CMD wget -qO- http://localhost:8060/api/actuator/health || exit 1

EXPOSE 8060

# JAVA_OPTS env'dan moslashtirish mumkin
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

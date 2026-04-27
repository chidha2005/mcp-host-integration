# Multi-stage build: see db-server/Dockerfile for design notes.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
COPY mcp-db-server/pom.xml              mcp-db-server/
COPY mcp-filesystem-server/pom.xml      mcp-filesystem-server/
COPY mcp-web-server/pom.xml             mcp-web-server/
COPY mcp-host/pom.xml                   mcp-host/

RUN mvn -B dependency:go-offline -pl web-server -am || true

COPY mcp-web-server/src mcp-web-server/src

RUN mvn -B -pl mcp-web-server -am -DskipTests clean package

# ----------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/mcp-web-server/target/mcp-web-server-1.0.0-SNAPSHOT.jar app.jar

RUN apk add --no-cache curl

EXPOSE 8092

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl -fsS http://localhost:8092/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

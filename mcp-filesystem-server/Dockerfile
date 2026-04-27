# Multi-stage build: see db-server/Dockerfile for design notes.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
COPY mcp-db-server/pom.xml              mcp-db-server/
COPY mcp-filesystem-server/pom.xml      mcp-filesystem-server/
COPY mcp-web-server/pom.xml             mcp-web-server/
COPY mcp-host/pom.xml                   mcp-host/

RUN mvn -B dependency:go-offline -pl mcp-filesystem-server -am || true

COPY mcp-filesystem-server/src mcp-filesystem-server/src

RUN mvn -B -pl mcp-filesystem-server -am -DskipTests clean package

# ----------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/mcp-filesystem-server/target/mcp-filesystem-server-1.0.0-SNAPSHOT.jar app.jar

# Pre-create the sandbox directory inside the container.
# Mount a host volume here at runtime if you want to persist files.
RUN mkdir -p /app/sandbox

# Bundle the sample file into the image so the FS tools have something to read
COPY mcp-filesystem-server/sandbox/ /app/sandbox/

RUN apk add --no-cache curl

EXPOSE 8091

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl -fsS http://localhost:8091/ || exit 1

# Override fs.root so it points at the in-container path
ENTRYPOINT ["java", "-jar", "app.jar", "--fs.root=/app/sandbox"]

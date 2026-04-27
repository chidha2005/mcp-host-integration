# ============================================================
# Stage 1: Build the entire multi-module project.
# We need the full project context because db-server's POM
# inherits from the parent POM at the project root.
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy parent POM and all module POMs first.
# This lets Docker cache the dependency download layer separately
# from source code changes — rebuilds are much faster.
COPY pom.xml .
COPY mcp-db-server/pom.xml              mcp-db-server/
COPY mcp-filesystem-server/pom.xml      mcp-filesystem-server/
COPY mcp-web-server/pom.xml             mcp-web-server/
COPY mcp-host/pom.xml                   mcp-host/

# Pre-download all dependencies (cached layer)
RUN mvn -B dependency:go-offline -pl mcp-db-server -am || true

# Now copy the source for this module and build only it
COPY mcp-db-server/src mcp-db-server/src

RUN mvn -B -pl mcp-db-server -am -DskipTests clean package

# ============================================================
# Stage 2: Slim runtime image with only the JRE + the JAR.
# ============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Pull just the built JAR from the builder stage
COPY --from=builder /build/mcp-db-server/target/mcp-db-server-1.0.0-SNAPSHOT.jar app.jar

# Add curl for the healthcheck
RUN apk add --no-cache curl

EXPOSE 8090

# Spring Boot Actuator would be cleaner for healthchecks but adds a dep.
# A simple GET against the MCP endpoint is good enough for the demo.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl -fsS http://localhost:8090/actuator/health || curl -fsS http://localhost:8090/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

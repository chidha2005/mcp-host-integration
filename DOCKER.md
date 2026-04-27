# Running the MCP Host Stack with Docker Compose

This is the Docker-based path to bring up all four services. It's the closest setup to a real production deployment — every service in its own container, on a shared private network, with health checks gating startup order.

## Prerequisites

You need Docker Desktop running with WSL2 integration enabled (Windows) or Docker Engine (Linux/Mac). Verify:

```bash
docker version
docker compose version
```

If Docker Desktop is installed but `docker` isn't found in your WSL2 terminal, open Docker Desktop → Settings → Resources → WSL Integration and toggle on your distro.

## One-Command Bring-Up

```bash
cd mcp-host
export ANTHROPIC_API_KEY=sk-ant-xxx
./scripts/docker-up.sh
```

The script builds all four images, starts the containers, and waits for the host to report healthy. First build takes 4–7 minutes (Maven downloads dependencies into each builder stage). Subsequent builds take 30–60 seconds because Docker caches the dependency layer.

If you'd rather not use the script:

```bash
export ANTHROPIC_API_KEY=sk-ant-xxx
docker compose up --build
```

That runs in the foreground so you see all four services' logs interleaved. Use `docker compose up --build -d` to detach.

## What Just Happened

Docker Compose did this in order:

1. **Built four images** from the four Dockerfiles, each using a multi-stage build: `maven:3.9-eclipse-temurin-21` to compile, then a slim `eclipse-temurin:21-jre-alpine` to actually run. Final images are around 250 MB instead of 1+ GB.
2. **Started the three MCP servers** (`db-server`, `fs-server`, `web-server`) in parallel.
3. **Ran health checks** on each one until they reported healthy.
4. **Started the host** — which is configured with `depends_on: condition: service_healthy`, so it only boots after all three servers are reachable.
5. **Exposed ports** 8080 (host), 8090 (db), 8091 (fs), 8092 (web) on `localhost` so you can hit them from your machine.

Inside the Docker network, the host reaches the three servers by service name (`http://db-server:8090`, etc.) — not by `localhost`. The compose file injects environment variables that override the URLs in `application.yml` at runtime, so the same JAR works in both bare-metal and container modes without code changes.

## Common Commands

```bash
# Watch logs from all services live
docker compose logs -f

# Just the host
docker compose logs -f host

# See container status and health
docker compose ps

# Restart just one service after a code change to that module
docker compose up --build -d db-server

# Tear everything down (containers + network)
docker compose down

# Tear down AND wipe volumes (the fs-sandbox volume)
docker compose down -v

# Hop into a container's shell to poke around
docker compose exec host sh

# Run an ad-hoc curl from inside the network
docker compose exec host curl -fsS http://db-server:8090/
```

## Try It Out

```bash
# DB tool path
curl 'http://localhost:8080/chat?q=How+many+CHECKING+transactions+failed+enrichment%3F'

# FS tool path
curl 'http://localhost:8080/chat?q=Read+vendor-notes.txt+and+summarize+the+latency+targets'

# Cross-server reasoning (DB + FS)
curl 'http://localhost:8080/chat?q=Compare+our+actual+Spade+success+rate+for+CHECKING+with+the+target+in+vendor-notes.txt'

# Web tool with allowlist
curl 'http://localhost:8080/chat?q=Fetch+https%3A%2F%2Fhttpbin.org%2Fjson+and+tell+me+what+fields+it+has'
```

## Iterating on Code Changes

When you edit code in one module, rebuild only that module's image:

```bash
docker compose up --build -d db-server
```

For host code changes, the host depends on the others being up, but they don't need to restart:

```bash
docker compose up --build -d host
```

## Resource Footprint

Approximate steady-state usage on Docker Desktop (Windows + WSL2):

| Service     | Memory | CPU (idle) |
|-------------|--------|------------|
| db-server   | ~280 MB| <1%        |
| fs-server   | ~250 MB| <1%        |
| web-server  | ~250 MB| <1%        |
| host        | ~320 MB| <1%        |
| **Total**   | ~1.1 GB| negligible |

Comfortable on 16 GB total RAM, even alongside the Starburst lakehouse stack from earlier.

## Troubleshooting

**"ANTHROPIC_API_KEY is not set"** — the compose file uses `${VAR:?}` syntax to fail fast if the key isn't exported. Run `export ANTHROPIC_API_KEY=sk-ant-xxx` in the same shell before `docker compose up`.

**Build hangs at "Downloading from central"** — Maven inside the builder stage is pulling dependencies. First build pulls ~200 MB of Spring Boot + Spring AI artifacts. Be patient. Subsequent builds use Docker's layer cache.

**Host container exits with "Connection refused" errors** — one of the three servers didn't come up healthy in time. Check `docker compose logs db-server` (or fs/web). Most often it's the H2 schema initialization failing on the db-server. Increase the health check `start_period` in `docker-compose.yml` if your machine is slow.

**Port already in use** — something else on your machine is using 8080/8090/8091/8092. Either stop that process or change the left side of the port mapping in `docker-compose.yml`: `"19090:8090"` would expose the db-server on host port 19090.

**Image rebuild not picking up code changes** — Docker's layer cache thinks nothing changed. Force a clean rebuild: `docker compose build --no-cache <service>`.

**Out of disk space** — multi-stage builds leave the intermediate `maven` images on disk. Clean them periodically: `docker image prune -a`.

## What This Maps to in Production

A few notes on how this would change for a real deployment at a place like Citizens Bank:

- The Anthropic API key would come from a secrets manager (Vault, AWS Secrets Manager), injected as an env var at runtime, never baked into images.
- The H2 in-memory database in `db-server` would be replaced with a connection to an actual Postgres — change `spring.datasource.url` and add the JDBC dep, no other code changes needed.
- TLS between the host and each MCP server would be mandatory. Spring AI 1.1+ has built-in MCP Security (OAuth2) for this.
- Each service would have Spring Boot Actuator's `/actuator/health` enabled with proper liveness/readiness probes, and the Docker health checks would target those instead of root paths.
- Container images would be scanned (Trivy, Grype) and pulled from a private registry, not built on every deploy.
- The whole compose file becomes a Helm chart for Kubernetes, with the same dependency graph encoded as init containers or readiness gates.

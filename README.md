# Spring AI MCP Host (Java) — DSM-flavored Demo

A faithful Java/Spring Boot implementation of the MCP architecture from your diagram:
**one host application** (Spring Boot + Anthropic Claude) maintains **three MCP clients**, each
connected 1:1 to a separate **MCP server** that wraps an external system.

## Architecture

```
                    ┌────────────────────────────────────┐
                    │         MCP HOST (port 8080)       │
                    │  Spring Boot + ChatClient + Claude │
                    │                                    │
                    │  ┌──────────┐ ┌──────────┐ ┌─────┐ │
                    │  │ Client A │ │ Client B │ │  C  │ │
                    │  └────┬─────┘ └────┬─────┘ └──┬──┘ │
                    └───────┼────────────┼──────────┼────┘
                            │ MCP/HTTP   │ MCP/HTTP │ MCP/HTTP
                  ┌─────────▼──┐  ┌──────▼──────┐ ┌─▼─────────┐
                  │ db-server  │  │ fs-server   │ │ web-server│
                  │ port 8090  │  │ port 8091   │ │ port 8092 │
                  │            │  │             │ │           │
                  │ Tools:     │  │ Tools:      │ │ Tools:    │
                  │ - distrib  │  │ - listFiles │ │ - fetchUrl│
                  │ - volume   │  │ - readFile  │ │           │
                  │ - top-merch│  │ - writeFile │ │           │
                  │ - failed   │  │             │ │           │
                  └─────┬──────┘  └──────┬──────┘ └─────┬─────┘
                        │                │              │
                  ┌─────▼──────┐  ┌──────▼──────┐ ┌─────▼─────┐
                  │   H2/PG    │  │  ./sandbox  │ │ httpbin,  │
                  │ (txns DB)  │  │  (filesys)  │ │ wikipedia │
                  └────────────┘  └─────────────┘ └───────────┘
```

Mapping to the diagram you shared:
- The diagram's **MCP Host** = the `host/` module
- Each **MCP Client** = auto-created by Spring AI's MCP client starter, one per `streamable-http.connections.*` entry in `application.yml`
- Each **MCP Server** = one of `db-server/`, `fs-server/`, `web-server/`
- The transport between client and server = **Streamable HTTP** (the modern MCP transport, replaces SSE)

## Project layout

```
mcp-host/
├── pom.xml                  # Parent POM (BOM imports)
├── host/                    # The MCP HOST application
│   └── src/main/...
├── db-server/               # MCP Server: transaction database
│   └── src/main/...
├── fs-server/               # MCP Server: sandboxed filesystem
│   └── src/main/...
├── web-server/              # MCP Server: web fetch (allowlisted)
│   └── src/main/...
└── scripts/
    ├── start-all.sh
    └── stop-all.sh
```

## Prerequisites

- Java 21
- Maven 3.9+
- An Anthropic API key (or swap the LLM — see "Switching LLM providers" below)

## Quickstart

```bash
# 1. Set your API key
export ANTHROPIC_API_KEY=sk-ant-xxxxxxxxxxxxxxxxx

# 2. Build everything
mvn -DskipTests clean package

# 3. Start all four services
./scripts/start-all.sh

# 4. Ask a question that requires the database
curl 'http://localhost:8080/chat?q=How+many+CHECKING+transactions+failed+enrichment%3F'

# 5. Ask one that requires the filesystem
curl 'http://localhost:8080/chat?q=What+does+the+vendor-notes.txt+file+say+about+performance+targets%3F'

# 6. Ask one that combines sources
curl 'http://localhost:8080/chat?q=Compare+the+actual+vendor+primacy+rate+for+CHECKING+with+the+target+in+vendor-notes.txt'

# 7. Stop everything
./scripts/stop-all.sh
```

## How the wiring works (the part most tutorials gloss over)

Look at `host/src/main/resources/application.yml`:

```yaml
spring:
  ai:
    mcp:
      client:
        streamable-http:
          connections:
            transaction-server: { url: http://localhost:8090 }
            filesystem-server:  { url: http://localhost:8091 }
            web-server:         { url: http://localhost:8092 }
        toolcallback:
          enabled: true
```

That's it. The `spring-ai-starter-mcp-client` dependency reads those three connections,
creates one MCP client per entry, calls each server's `tools/list` endpoint at startup,
and aggregates every discovered tool into a single `ToolCallbackProvider` bean. The
host's `ChatClient` gets all of them attached, so the LLM sees a unified tool palette.

When you ask "how many CHECKING transactions failed enrichment?", Claude:
1. Looks at all available tools and their descriptions
2. Picks `getFailedEnrichmentCounts` from the transaction-server
3. Spring AI's MCP client runtime invokes it over HTTP
4. The result flows back into the LLM, which writes a natural-language answer

## Things to try (and why each one matters)

| Question | What it teaches |
|----------|----------------|
| "How many CHECKING transactions did we have in the last 7 days?" | Tool selection by description matching |
| "What's the total volume across all account types in the last 30 days?" | LLM calls the same tool multiple times with different args |
| "Read vendor-notes.txt and summarize the latency targets" | Cross-server: filesystem tool used standalone |
| "Compare our actual Spade success rate to the target in vendor-notes.txt" | Cross-server reasoning: DB tool + FS tool combined |
| "Fetch https://httpbin.org/json and tell me what's there" | Web tool with allowlist |
| "Fetch https://evil.com/x" | Allowlist rejection — see the guardrail in action |

## Switching LLM providers

The MCP architecture is LLM-agnostic. To swap Anthropic for OpenAI:

```xml
<!-- in host/pom.xml, replace the anthropic dep with: -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

```yaml
# in host/application.yml, replace anthropic config with:
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.openai.chat.options.model: gpt-4o
```

Or use Ollama for fully local inference — same pattern. **You change one dependency
and a few config lines. The MCP servers don't change at all.** That's the whole
point of MCP.

## Production hardening checklist

This is a learning project. Before anything resembling production:

1. **Auth on every server.** Put OAuth2 / mTLS / API keys in front of each MCP
   server. Spring AI 1.1+ has MCP Security support built in.
2. **Tool input validation.** The LLM controls tool arguments. Validate them on
   the server side as if they were user input — because effectively they are.
3. **Audit logging.** Log every tool invocation with caller identity, args, and
   result size. This is non-negotiable in a regulated environment.
4. **Rate limits per tool.** A confused LLM can call a tool in a loop. Cap it.
5. **PII masking on tool outputs.** Reuse your existing `@MaskPii` AOP — apply
   it to the methods on `TransactionTools`.
6. **Don't expose write tools without explicit user confirmation flow.** The
   `writeFile` tool should require a separate approval step in production.
7. **Network egress controls.** The web server's allowlist is a starting point,
   not a real control. Force outbound through your bank's egress proxy.

## How this maps to your DSM 2.0 work

The `db-server` is intentionally modeled on DSM 2.0:
- The schema mirrors your enrichment output (account_type, vendor, status)
- The tools mirror queries you already run on Datadog dashboards (vendor primacy
  distribution, failed enrichment counts)
- The vendor-notes file mirrors the kind of operational context that lives in
  Confluence today

A natural next step would be to point the `db-server` at the actual Postgres
schema you use for enrichment routing rules. The MCP server pattern means an
LLM-powered Slack bot could answer engineering questions like "what's our Spade
success rate by account type today?" without anyone hand-writing SQL.

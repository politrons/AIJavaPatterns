# AI Session Notes: a2a-protocol

This module is the first AI-native Java pattern in the `AIJavaPatterns` repository. It demonstrates two local agents communicating through the A2A Java SDK and using Ollama for local LLM responses.

## Module Purpose

`a2a-protocol` is a Quarkus-based Java PoC with two specialized agents:

- `Filesystem Agent`: handles filesystem-oriented reasoning and will later connect to a filesystem MCP server.
- `Gmail Agent`: handles Gmail-oriented reasoning and will later connect to a Gmail MCP server.

The agents currently do not execute real filesystem or Gmail actions. They reason locally through Ollama, expose their capabilities through A2A Agent Cards, and delegate requests to each other when the peer agent has a better skill match.

## Maven Layout

The repository root is a Maven parent project:

```text
AIJavaPatterns/
  pom.xml
  a2a-protocol/
    pom.xml
    README.md
    agent.md
    src/main/java/com/example/a2apoc/
    src/main/resources/application.properties
    src/test/java/com/example/a2apoc/
```

Use root-level Maven commands when possible:

```bash
mvn test
mvn -pl a2a-protocol test
```

Run one agent from the root:

```bash
mvn -pl a2a-protocol quarkus:dev -Dquarkus.profile=filesystem
mvn -pl a2a-protocol quarkus:dev -Dquarkus.profile=gmail
```

## Runtime Profiles

Configuration lives in `src/main/resources/application.properties`.

- `filesystem` profile:
  - HTTP port: `8001`
  - `agent.kind=filesystem`
  - peer URL: `http://127.0.0.1:8002`
  - Ollama model: `llama3.2:3b`

- `gmail` profile:
  - HTTP port: `8002`
  - `agent.kind=gmail`
  - peer URL: `http://127.0.0.1:8001`
  - Ollama model: `llama3.1:8b`

If changing a port, update both `quarkus.http.port` and `agent.base-url` for that profile. The `agent.base-url` is published in the Agent Card and must match the reachable server URL.

## Important Classes

- `AgentCardFactory`: builds the public A2A Agent Card from the active profile.
- `AgentCardProducer`: produces the `@PublicAgentCard` bean consumed by the A2A SDK.
- `AgentExecutorProducer`: produces the A2A SDK `AgentExecutor` bean. This is the entry point for inbound A2A requests.
- `AgentRuntime`: contains the core routing flow: score local skill, compare peers, delegate if useful, otherwise answer locally.
- `AgentProfiles`: defines filesystem and Gmail profiles, skills, tags, examples, and system prompts.
- `SkillMatcher`: performs lightweight lexical scoring against Agent Card skills.
- `A2AAgentClient`: discovers peer Agent Cards and sends A2A messages through the REST transport.
- `OllamaLanguageModelClient`: calls Ollama `/api/chat`.
- `ResponseExtractor`: converts A2A SDK message/task responses into the local `AgentResponse` record.
- `A2ARecords`: centralizes all records used by the module.
- `DemoClient`: CLI helper for manually sending prompts to the agents.

## A2A Request Flow

Inbound request:

```text
A2A HTTP request
  -> A2A Java SDK Quarkus route
  -> AgentExecutorProducer.agentExecutor()
  -> AgentRuntime.handle(...)
  -> optional delegation through A2AAgentClient
  -> local Ollama fallback when delegation is not selected or fails
```

Agent Card discovery:

```text
Peer URL
  -> /.well-known/agent-card.json
  -> A2AAgentClient.getAgentCard(...)
  -> cached in AgentRuntime
```

Delegation metadata is stored under the `delegation` metadata key:

- `hopCount`: prevents infinite delegation loops.
- `visitedAgents`: prevents returning to agents already visited in the current chain.

## Design Rules For Future AI Sessions

- Keep documentation and user-facing logs in English.
- Keep the module self-contained under `a2a-protocol`.
- Do not move generated `target/` content into source-controlled structure.
- Prefer `Optional` for normal absence in domain flow. Avoid returning `null` from our own methods.
- Treat `null` from A2A SDK, Jackson, or external metadata as boundary input and normalize it at the edge.
- Keep records in `A2ARecords` unless there is a strong reason to split them again.
- Keep the runtime testable without starting HTTP servers or calling Ollama.
- Do not add real filesystem or Gmail side effects yet; those should wait for dedicated MCP integration.
- Preserve Quarkus CDI producers unless replacing the A2A SDK integration deliberately.

## Verification

Before finishing changes in this module, run:

```bash
mvn -pl a2a-protocol test
```

For structural changes at the repository level, run:

```bash
mvn test
```

Expected current baseline:

- `AgentRuntimeTest`
- 3 tests
- 0 failures

## Common Development Tasks

Add or tune an agent skill:

1. Update `AgentProfiles`.
2. Adjust examples/tags used by `SkillMatcher`.
3. Add or update tests in `AgentRuntimeTest`.

Change routing behavior:

1. Update `AgentRuntime`.
2. Keep delegation failures non-fatal unless the user explicitly wants hard failures.
3. Preserve hop-count and visited-agent handling.

Integrate a future MCP:

1. Add the MCP client behind a small interface.
2. Inject it into the relevant agent runtime path.
3. Keep filesystem and Gmail capabilities scoped to their own agent profile.
4. Add tests using fakes, not real local filesystem/Gmail calls.

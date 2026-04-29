# a2a-protocol

Java PoC with two local Ollama-backed agents that communicate with each other through the official A2A Java SDK.

This module belongs to the `AIJavaPatterns` Maven multi-module repository.

## Agents

- `Filesystem Agent` (`llama3.2:3b`, port `8001`): reasoning about files, paths, directories, read/write operations, and future filesystem MCP integration.
- `Gmail Agent` (`llama3.1:8b`, port `8002`): reasoning about Gmail, inboxes, drafts, replies, labels, and future Gmail MCP integration.

No MCP server is connected yet. The agents do not execute real filesystem or Gmail actions; they use Ollama to answer within their own domain and delegate through A2A when the other agent has a better skill match.

## Requirements

- Java 17+
- Maven 3.9+
- Ollama running at `http://localhost:11434`
- Local models:

```bash
ollama pull llama3.2:3b
ollama pull llama3.1:8b
```

## Run Agents

From this module directory, run:

Terminal 1:

```bash
mvn quarkus:dev -Dquarkus.profile=filesystem
```

Terminal 2:

```bash
mvn quarkus:dev -Dquarkus.profile=gmail
```

## Demo

Send the request to the first available agent and let it decide whether to delegate:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.a2apoc.DemoClient \
  -Dexec.args="--prompt 'Send a Gmail reply to leadership with this message'"
```

Force the filesystem agent as the entry point to see delegation to Gmail:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.a2apoc.DemoClient \
  -Dexec.args="--entry-agent http://127.0.0.1:8001 --prompt 'Draft a Gmail response for this customer issue'"
```

Force the Gmail agent as the entry point to see delegation to the filesystem agent:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.a2apoc.DemoClient \
  -Dexec.args="--entry-agent http://127.0.0.1:8002 --prompt 'List files under src/main and summarize the project structure'"
```

## Tests

```bash
mvn test
```

The tests validate routing without starting HTTP servers or calling Ollama.

# kafka-llm-agent

This module implements an Ollama-backed Kafka agent in Java.

The PoC demonstrates one of the most common current patterns between LLMs and Kafka: consume business events from an input topic, enrich them with an LLM, and publish the enriched event to a downstream topic.

## Standard Pattern

The concrete use case in this module is support ticket enrichment:

1. A raw support ticket event arrives on Kafka.
2. The agent asks Ollama to classify the ticket.
3. Ollama returns structured JSON with category, priority, sentiment, routing queue, summary, and suggested reply.
4. The agent publishes the enriched event to another Kafka topic.

This is a standard event-driven AI pattern because Kafka remains the system of flow control while the LLM is used as a stateless enrichment step.

## Runtime Flow

```text
support-tickets.raw topic
    |
    v
KafkaTicketAgentRunner
    |
    +--> EventEnrichmentAgent
             |
             +--> TicketEnrichmentService
                      |
                      +--> TicketPromptFactory
                      +--> LanguageModelClient
                               |
                               +--> OllamaLanguageModelClient
    |
    v
support-tickets.enriched topic
```

## Package Map

- `com.example.kafkallmagent`: command-line entry point.
- `com.example.kafkallmagent.business`: enrichment orchestration and prompt building.
- `com.example.kafkallmagent.llm`: Ollama client and model boundary.
- `com.example.kafkallmagent.messaging`: Kafka consumer/producer runner.
- `com.example.kafkallmagent.model`: immutable event and enrichment records.

## Requirements

- Java 17+
- Maven 3.9+
- Ollama running at `http://localhost:11434`
- Kafka reachable at `localhost:9092`
- Local Ollama model:

```bash
ollama pull llama3.2:3b
```

## Command Context

All commands in this README assume your current directory is:

```bash
/Users/politrons/development/AIJavaPatterns/kafka-llm-agent
```

If you are in the repository root instead, either `cd` into this module first or use `-pl kafka-llm-agent`.

## Run The Tests

```bash
mvn test
```

The tests use a fake `LanguageModelClient`, so they validate the enrichment workflow without requiring Kafka or Ollama.

## Run The Agent

The agent creates the input and output topics automatically if they do not exist yet.

## End-To-End Demo

1. Publish the sample ticket event to Kafka:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.kafkallmagent.DemoProducerMain
```

2. Run the agent once to consume, classify, and publish the enriched event:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.kafkallmagent.KafkaTicketAgentMain \
  -Dexec.args="--bootstrap-servers localhost:9092 --from-beginning --poll-once"
```

3. If you want to keep polling continuously:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.kafkallmagent.KafkaTicketAgentMain
```

## Run The Agent

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.kafkallmagent.KafkaTicketAgentMain \
  -Dexec.args="--bootstrap-servers localhost:9092 --from-beginning --poll-once"
```

The default topics are:

- `support-tickets.raw`
- `support-tickets.enriched`

Run continuously:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.kafkallmagent.KafkaTicketAgentMain
```

Use a custom Ollama model:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.kafkallmagent.KafkaTicketAgentMain \
  -Dexec.args="--model llama3.1:8b --poll-once"
```

Use custom topics:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.kafkallmagent.KafkaTicketAgentMain \
  -Dexec.args="--input-topic tickets.raw --output-topic tickets.enriched --poll-once"
```

Use `--from-beginning` when you want the demo consumer to force a fresh Kafka consumer group and reread the topic from the earliest offset:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.kafkallmagent.KafkaTicketAgentMain \
  -Dexec.args="--from-beginning --poll-once"
```

## Example Input Event

See [support-ticket.json](/Users/politrons/development/AIJavaPatterns/kafka-llm-agent/src/main/resources/sample/support-ticket.json).

## Main Classes

- `KafkaTicketAgentMain`: parses CLI options and starts the Kafka runner.
- `DemoProducerMain`: publishes the sample support ticket event to the Kafka input topic.
- `KafkaTopicBootstrapper`: creates the input and output topics if they do not exist yet.
- `KafkaTicketAgentRunner`: polls Kafka, deserializes raw events, enriches them, and publishes JSON.
- `EventEnrichmentAgent`: application-level enrichment orchestration.
- `TicketEnrichmentService`: calls Ollama and parses structured JSON.
- `TicketPromptFactory`: builds the classification prompt for the LLM.
- `OllamaLanguageModelClient`: Java HTTP client for Ollama's `/api/generate` endpoint.

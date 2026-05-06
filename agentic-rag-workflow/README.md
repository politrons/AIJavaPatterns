# agentic-rag-workflow

This module implements an Ollama-backed Retrieval-Augmented Generation workflow in Java.

The goal is educational: every class documents one AI-native responsibility, and the package structure keeps the workflow easy to follow.

## What This Module Teaches

1. Load trusted source documents.
2. Split documents into traceable chunks.
3. Retrieve relevant chunks for a user question.
4. Build a grounded prompt from retrieved evidence.
5. Ask Ollama to generate an answer constrained by that evidence.
6. Return citations and evidence previews with the generated answer.

## Runtime Flow

```text
User question
    |
    v
RagWorkflow
    |
    +--> InMemoryKnowledgeBase
    |        |
    |        +--> TextChunker
    |        +--> KeywordRetriever
    |
    +--> GroundedAnswerComposer
             |
             +--> LanguageModelClient
                    |
                    +--> OllamaLanguageModelClient
```

The key design rule is that business code retrieves evidence before the LLM is called. The model receives the question plus selected chunks, not unrestricted repository context.

## Package Map

- `com.example.agenticrag`: command-line entry point.
- `com.example.agenticrag.business`: workflow orchestration, chunking, retrieval, and grounded prompt composition.
- `com.example.agenticrag.llm`: LLM provider boundary and Ollama HTTP client.
- `com.example.agenticrag.model`: immutable records used across the workflow.
- `com.example.agenticrag.utils`: file loading and text normalization helpers.

## Requirements

- Java 17+
- Maven 3.9+
- Ollama running at `http://localhost:11434`
- Local model:

```bash
ollama pull llama3.2:3b
```

You can use another Ollama model with the `--model` option.

## Command Context

All commands in this README assume your current directory is:

```bash
/Users/politrons/development/AIJavaPatterns/agentic-rag-workflow
```

If you are in the repository root instead, either `cd` into this module first or use `-pl agentic-rag-workflow`.

## Run The Tests

```bash
mvn test
```

The tests use a fake `LanguageModelClient` so they validate the Java workflow without requiring a running Ollama process.

## Run The Demo

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.agenticrag.DemoClient \
  -Dexec.args="--question 'What are the steps in the RAG runtime flow?'"
```

By default, the demo indexes the curated document at `src/main/resources/knowledge/rag-workflow.md`.

Use a custom Ollama model:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.agenticrag.DemoClient \
  -Dexec.args="--model llama3.1:8b --question 'How does this module use citations?'"
```

Use custom source documents:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.agenticrag.DemoClient \
  -Dexec.args="--question 'How does this module avoid hallucinations?' --doc README.md"
```

Use a custom Ollama endpoint:

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.agenticrag.DemoClient \
  -Dexec.args="--ollama-url http://localhost:11434 --question 'Explain the RAG flow'"
```

You can also set the endpoint with:

```bash
export OLLAMA_BASE_URL=http://localhost:11434
```

## Main Classes

- `DemoClient`: parses CLI options, loads documents, and runs the workflow.
- `RagWorkflow`: coordinates retrieval and answer generation.
- `InMemoryKnowledgeBase`: stores chunks and exposes retrieval.
- `TextChunker`: creates traceable evidence chunks.
- `KeywordRetriever`: scores chunks against the question.
- `GroundedAnswerComposer`: creates the grounded prompt and calls the LLM boundary.
- `LanguageModelClient`: interface used by business code.
- `OllamaLanguageModelClient`: Java HTTP client for Ollama's `/api/generate` endpoint.
- `GroundedAnswer`: answer, citations, and evidence previews returned to callers.

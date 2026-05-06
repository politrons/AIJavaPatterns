# Agentic RAG Workflow

This document is the default knowledge source for the `agentic-rag-workflow` demo.

## Runtime Flow

The workflow builds a grounded RAG answer through these steps:

1. The user submits a question.
2. `DocumentLoader` loads trusted source documents from disk.
3. `TextChunker` splits each source document into traceable chunks.
4. `InMemoryKnowledgeBase` stores the chunks for retrieval.
5. `KeywordRetriever` selects the chunks that best match the user question.
6. `GroundedAnswerComposer` creates a prompt that contains the question and the selected evidence.
7. `OllamaLanguageModelClient` sends the grounded prompt to Ollama.
8. The workflow returns a `GroundedAnswer` with the generated answer, citations, and evidence previews.

## Grounding Rule

The LLM is not allowed to answer from unrestricted context. It receives only the user question and the retrieved evidence chunks selected by the Java workflow.

## Package Responsibilities

- `business`: owns the RAG workflow, chunking, retrieval, and grounded prompt composition.
- `llm`: owns the language model boundary and the Ollama HTTP implementation.
- `model`: owns immutable records shared by the workflow.
- `utils`: owns small helper classes for loading documents and normalizing text.

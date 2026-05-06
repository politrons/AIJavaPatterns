package com.example.agenticrag;

import com.example.agenticrag.business.RagWorkflow;
import com.example.agenticrag.llm.OllamaLanguageModelClient;
import com.example.agenticrag.model.GroundedAnswer;
import com.example.agenticrag.model.RagRequest;
import com.example.agenticrag.utils.DocumentLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line demo for the Ollama-backed RAG workflow.
 */
public final class DemoClient {

    private static final String DEFAULT_QUESTION = "What are the steps in the RAG runtime flow?";

    private DemoClient() {
    }

    public static void main(String[] args) throws IOException {
        DemoOptions options = DemoOptions.parse(args);
        Path repoRoot = findRepositoryRoot(Path.of("").toAbsolutePath().normalize());
        List<Path> documentPaths = options.documentPaths().isEmpty()
                ? defaultDocumentPaths(repoRoot)
                : options.documentPaths();

        DocumentLoader documentLoader = new DocumentLoader();
        OllamaLanguageModelClient languageModelClient = new OllamaLanguageModelClient(
                options.ollamaBaseUrl(),
                options.model()
        );
        RagWorkflow workflow = RagWorkflow.fromDocuments(documentLoader.loadAll(documentPaths), languageModelClient);
        GroundedAnswer answer = workflow.answer(new RagRequest(options.question(), options.maxEvidence()));

        System.out.println(answer.toMarkdown());
    }

    private static List<Path> defaultDocumentPaths(Path repoRoot) {
        return List.of(
                repoRoot.resolve("agentic-rag-workflow/src/main/resources/knowledge/rag-workflow.md")
        ).stream()
                .filter(Files::isRegularFile)
                .toList();
    }

    private static Path findRepositoryRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return start;
    }

    private record DemoOptions(
            String question,
            int maxEvidence,
            String ollamaBaseUrl,
            String model,
            List<Path> documentPaths
    ) {

        private static DemoOptions parse(String[] args) {
            String question = DEFAULT_QUESTION;
            int maxEvidence = RagRequest.DEFAULT_MAX_EVIDENCE;
            String ollamaBaseUrl = OllamaLanguageModelClient.baseUrlFromEnvironment();
            String model = OllamaLanguageModelClient.DEFAULT_MODEL;
            List<Path> documentPaths = new ArrayList<>();

            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                switch (arg) {
                    case "--question" -> question = requireValue(args, ++index, "--question");
                    case "--max-evidence" -> maxEvidence = Integer.parseInt(requireValue(args, ++index, "--max-evidence"));
                    case "--ollama-url" -> ollamaBaseUrl = requireValue(args, ++index, "--ollama-url");
                    case "--model" -> model = requireValue(args, ++index, "--model");
                    case "--doc" -> documentPaths.add(Path.of(requireValue(args, ++index, "--doc")).toAbsolutePath().normalize());
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return new DemoOptions(question, maxEvidence, ollamaBaseUrl, model, List.copyOf(documentPaths));
        }

        private static String requireValue(String[] args, int index, String optionName) {
            if (index >= args.length) {
                throw new IllegalArgumentException(optionName + " requires a value");
            }
            return args[index];
        }
    }
}

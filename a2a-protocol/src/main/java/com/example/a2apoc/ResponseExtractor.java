package com.example.a2apoc;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.a2apoc.A2ARecords.AgentResponse;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;

public final class ResponseExtractor {
    private ResponseExtractor() {
    }

    /**
     * Converts an immediate A2A message response into the local response shape.
     */
    public static AgentResponse fromMessage(Message message) {
        return AgentResponse.of(textFromParts(message.parts()), message.metadata());
    }

    /**
     * Extracts the best available text response from an A2A task.
     *
     * <p>The SDK may return text either in the task status message or in task
     * artifacts, so this method checks both locations before returning an empty
     * response.
     */
    public static AgentResponse fromTask(Task task) {
        if (task.status() != null && task.status().message() != null) {
            String text = textFromParts(task.status().message().parts());
            if (!text.isBlank()) {
                return AgentResponse.of(text, task.status().message().metadata());
            }
        }

        if (task.artifacts() != null && !task.artifacts().isEmpty()) {
            String text = task.artifacts().stream()
                    .map(Artifact::parts)
                    .map(ResponseExtractor::textFromParts)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.joining("\n"));
            if (!text.isBlank()) {
                Map<String, Object> metadata = task.metadata() == null ? Map.of() : task.metadata();
                return AgentResponse.of(text, metadata);
            }
        }

        return AgentResponse.of("", task.metadata());
    }

    /**
     * Joins all text parts from a generic A2A parts list and ignores non-text
     * parts.
     */
    public static String textFromParts(List<Part<?>> parts) {
        if (parts == null) {
            return "";
        }
        return parts.stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .collect(Collectors.joining("\n"));
    }
}

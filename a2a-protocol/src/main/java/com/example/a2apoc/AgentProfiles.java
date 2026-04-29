package com.example.a2apoc;

import java.util.List;
import java.util.Locale;

import com.example.a2apoc.A2ARecords.AgentProfile;

import org.a2aproject.sdk.spec.AgentSkill;

public final class AgentProfiles {
    private static final List<String> TEXT_MODES = List.of("text");

    private AgentProfiles() {
    }

    /**
     * Resolves the configured agent kind to the profile that will be exposed in
     * the local Agent Card.
     */
    public static AgentProfile forKind(String kind) {
        String normalized = kind == null ? "" : kind.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case "filesystem" -> filesystem();
            case "gmail" -> gmail();
            default -> throw new IllegalArgumentException("Unsupported agent.kind: " + kind);
        };
    }

    /**
     * Defines the filesystem specialist profile, including the routing skill and
     * the local system prompt used by Ollama.
     */
    private static AgentProfile filesystem() {
        AgentSkill skill = AgentSkill.builder()
                .id("filesystem-operations")
                .name("Filesystem planning and operations")
                .description("Handles local file, path, directory, search, read, write, and organization requests.")
                .tags(List.of(
                        "filesystem", "file", "files", "folder", "directory", "path",
                        "read", "write", "list", "search", "scan", "tree",
                        "project", "workspace", "source", "configuration"))
                .examples(List.of(
                        "List files under a project directory",
                        "Read a README and summarize what changed",
                        "Create a plan to organize local folders",
                        "Find Java files and identify where configuration is defined"))
                .inputModes(TEXT_MODES)
                .outputModes(TEXT_MODES)
                .build();

        return new AgentProfile(
                "filesystem",
                "Filesystem Agent",
                "Specialist in local filesystem reasoning and future filesystem MCP operations.",
                """
                You are a filesystem-specialized agent. Your area is local files, directories, paths,
                project structure, file search, read/write plans, and safe operational steps.

                You do not currently have a filesystem MCP or direct file tools inside the agent runtime.
                If the user asks for an actual local filesystem operation, explain the intended action,
                required path/context, and expected result. When a request is primarily about Gmail,
                email, inboxes, drafts, labels, recipients, or message handling, delegate instead of
                answering locally.
                """,
                List.of(skill));
    }

    /**
     * Defines the Gmail specialist profile, including the routing skill and the
     * local system prompt used by Ollama.
     */
    private static AgentProfile gmail() {
        AgentSkill skill = AgentSkill.builder()
                .id("gmail-account-operations")
                .name("Gmail account planning and operations")
                .description("Handles Gmail, inbox, drafts, labels, email search, replies, forwarding, and message triage.")
                .tags(List.of(
                        "gmail", "email", "mail", "inbox", "draft", "reply", "forward",
                        "label", "recipient", "subject", "attachment", "send", "message",
                        "thread", "unread", "triage", "mailbox"))
                .examples(List.of(
                        "Draft a reply to this Gmail thread",
                        "Find important unread emails and summarize them",
                        "Create a Gmail label and organize messages",
                        "Prepare an email draft to reply to this customer"))
                .inputModes(TEXT_MODES)
                .outputModes(TEXT_MODES)
                .build();

        return new AgentProfile(
                "gmail",
                "Gmail Agent",
                "Specialist in Gmail account reasoning and future Gmail MCP operations.",
                """
                You are a Gmail-specialized agent. Your area is Gmail, inbox triage, message search,
                drafts, replies, labels, attachments, recipients, and email workflows.

                You do not currently have a Gmail MCP or direct mailbox access inside the agent runtime.
                If the user asks for an actual mailbox operation, explain what you would do once the
                Gmail MCP is connected and ask only for missing details that are essential. When a request
                is primarily about local files, directories, paths, or filesystem changes, delegate instead
                of answering locally.
                """,
                List.of(skill));
    }
}

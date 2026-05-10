package com.example.kafkallmagent;

import com.example.kafkallmagent.business.EventEnrichmentAgent;
import com.example.kafkallmagent.business.TicketEnrichmentService;
import com.example.kafkallmagent.business.TicketPromptFactory;
import com.example.kafkallmagent.llm.OllamaLanguageModelClient;
import com.example.kafkallmagent.messaging.KafkaTicketAgentRunner;
import com.example.kafkallmagent.messaging.KafkaTicketTopics;
import com.example.kafkallmagent.messaging.KafkaTopicBootstrapper;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * Command-line entry point for the Kafka plus Ollama enrichment agent.
 */
public final class KafkaTicketAgentMain {

    private static final Logger LOGGER = Logger.getLogger(KafkaTicketAgentMain.class.getName());

    private KafkaTicketAgentMain() {
    }

    /**
     * Starts the Kafka consumer loop or a single poll cycle for the support-ticket enrichment agent.
     * The method parses command-line options, ensures the required topics exist, wires the prompt,
     * LLM client, business service, and Kafka runner, and then hands control to the runner.
     *
     * @param args command-line options such as bootstrap servers, topic names, group id, Ollama URL,
     *             model name, and polling mode flags
     */
    public static void main(String[] args) {
        CliOptions options = CliOptions.parse(args);
        LOGGER.info(() -> "Starting Kafka LLM agent. bootstrapServers='%s', inputTopic='%s', outputTopic='%s', groupId='%s', model='%s', ollamaUrl='%s', pollOnce=%s"
                .formatted(
                        options.bootstrapServers(),
                        options.inputTopic(),
                        options.outputTopic(),
                        options.groupId(),
                        options.model(),
                        options.ollamaBaseUrl(),
                        options.pollOnce()
                ));

        KafkaTicketTopics topics = new KafkaTicketTopics(options.inputTopic(), options.outputTopic(), options.groupId());
        //Ensure topic exist or create new one
        new KafkaTopicBootstrapper().ensureTopicsExist(options.bootstrapServers(), topics);

        TicketPromptFactory promptFactory = new TicketPromptFactory();
        OllamaLanguageModelClient languageModelClient = new OllamaLanguageModelClient(
                options.ollamaBaseUrl(),
                options.model()
        );
        TicketEnrichmentService enrichmentService = new TicketEnrichmentService(promptFactory, languageModelClient);
        EventEnrichmentAgent agent = new EventEnrichmentAgent(enrichmentService);

        try (KafkaTicketAgentRunner runner = KafkaTicketAgentRunner.createDefault(
                options.bootstrapServers(),
                topics,
                agent
        )) {
            if (options.pollOnce()) {
                LOGGER.info("Running a single Kafka poll cycle.");
                runner.runOnce();
                LOGGER.info("Kafka LLM agent finished the single poll cycle.");
                return;
            }
            LOGGER.info("Running Kafka LLM agent in continuous polling mode.");
            runner.run();
        }
    }

    private record CliOptions(
            String bootstrapServers,
            String inputTopic,
            String outputTopic,
            String groupId,
            String ollamaBaseUrl,
            String model,
            boolean fromBeginning,
            boolean pollOnce
    ) {

        private static CliOptions parse(String[] args) {
            String bootstrapServers = "localhost:9092";
            String inputTopic = "support-tickets.raw";
            String outputTopic = "support-tickets.enriched";
            String groupId = "support-ticket-llm-agent";
            String ollamaBaseUrl = OllamaLanguageModelClient.baseUrlFromEnvironment();
            String model = OllamaLanguageModelClient.DEFAULT_MODEL;
            boolean fromBeginning = false;
            boolean pollOnce = false;

            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                switch (arg) {
                    case "--bootstrap-servers" -> bootstrapServers = requireValue(args, ++index, "--bootstrap-servers");
                    case "--input-topic" -> inputTopic = requireValue(args, ++index, "--input-topic");
                    case "--output-topic" -> outputTopic = requireValue(args, ++index, "--output-topic");
                    case "--group-id" -> groupId = requireValue(args, ++index, "--group-id");
                    case "--ollama-url" -> ollamaBaseUrl = requireValue(args, ++index, "--ollama-url");
                    case "--model" -> model = requireValue(args, ++index, "--model");
                    case "--from-beginning" -> fromBeginning = true;
                    case "--poll-once" -> pollOnce = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            if (fromBeginning) {
                groupId = groupId + "-from-beginning-" + Instant.now().toEpochMilli();
            }

            return new CliOptions(
                    bootstrapServers,
                    inputTopic,
                    outputTopic,
                    groupId,
                    ollamaBaseUrl,
                    model,
                    fromBeginning,
                    pollOnce
            );
        }

        private static String requireValue(String[] args, int index, String optionName) {
            if (index >= args.length) {
                throw new IllegalArgumentException(optionName + " requires a value");
            }
            return args[index];
        }
    }
}

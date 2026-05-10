package com.example.kafkallmagent;

import com.example.kafkallmagent.messaging.KafkaTicketTopics;
import com.example.kafkallmagent.messaging.KafkaTopicBootstrapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Publishes the sample support ticket event to Kafka for an end-to-end demo.
 */
public final class DemoProducerMain {

    private static final Logger LOGGER = Logger.getLogger(DemoProducerMain.class.getName());
    private static final String SAMPLE_RESOURCE = "/sample/support-ticket.json";
    private static final int PRODUCER_TIMEOUT_MS = 10_000;

    private DemoProducerMain() {
    }

    /**
     * Publishes the sample support-ticket JSON event to the raw Kafka topic used by the demo.
     * This method also ensures the raw and enriched topics exist first so the end-to-end flow can
     * be run from a clean local broker without manual topic provisioning.
     *
     * @param args command-line options such as bootstrap servers and topic names
     * @throws Exception if the sample payload cannot be loaded or Kafka cannot accept the record
     */
    public static void main(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        KafkaTicketTopics topics = new KafkaTicketTopics(options.inputTopic(), options.outputTopic(), options.groupId());
        LOGGER.info(() -> "Starting demo producer. bootstrapServers='%s', inputTopic='%s'."
                .formatted(options.bootstrapServers(), topics.inputTopic()));
        new KafkaTopicBootstrapper().ensureTopicsExist(options.bootstrapServers(), topics);

        LOGGER.info(() -> "Publishing sample support ticket to topic '%s' on bootstrapServers '%s'."
                .formatted(topics.inputTopic(), options.bootstrapServers()));

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties(options.bootstrapServers()))) {
            String payload = loadSampleTicketJson();
            LOGGER.info(() -> "Sending Kafka record with key 'TCK-1001' to topic '%s'."
                    .formatted(topics.inputTopic()));
            producer.send(new ProducerRecord<>(topics.inputTopic(), "TCK-1001", payload))
                    .get(PRODUCER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            producer.flush();
            LOGGER.info(() -> "Published sample support ticket with key 'TCK-1001' to topic '%s'."
                    .formatted(topics.inputTopic()));
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "Timed out while publishing to Kafka. If Kafka is running in Docker, verify advertised.listeners points to localhost:9092.",
                    exception
            );
        }
    }

    private static Properties producerProperties(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, PRODUCER_TIMEOUT_MS);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, PRODUCER_TIMEOUT_MS);
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, PRODUCER_TIMEOUT_MS);
        return properties;
    }

    private static String loadSampleTicketJson() throws IOException {
        try (InputStream inputStream = DemoProducerMain.class.getResourceAsStream(SAMPLE_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not find sample resource: " + SAMPLE_RESOURCE);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record CliOptions(
            String bootstrapServers,
            String inputTopic,
            String outputTopic,
            String groupId
    ) {

        private static CliOptions parse(String[] args) {
            String bootstrapServers = "localhost:9092";
            String inputTopic = "support-tickets.raw";
            String outputTopic = "support-tickets.enriched";
            String groupId = "support-ticket-llm-agent";

            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                switch (arg) {
                    case "--bootstrap-servers" -> bootstrapServers = requireValue(args, ++index, "--bootstrap-servers");
                    case "--input-topic" -> inputTopic = requireValue(args, ++index, "--input-topic");
                    case "--output-topic" -> outputTopic = requireValue(args, ++index, "--output-topic");
                    case "--group-id" -> groupId = requireValue(args, ++index, "--group-id");
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return new CliOptions(bootstrapServers, inputTopic, outputTopic, groupId);
        }

        private static String requireValue(String[] args, int index, String optionName) {
            if (index >= args.length) {
                throw new IllegalArgumentException(optionName + " requires a value");
            }
            return args[index];
        }
    }
}

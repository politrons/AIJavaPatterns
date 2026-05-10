package com.example.kafkallmagent.messaging;

import com.example.kafkallmagent.business.EventEnrichmentAgent;
import com.example.kafkallmagent.model.EnrichedSupportTicketEvent;
import com.example.kafkallmagent.model.SupportTicketEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Bridges Kafka input/output topics with the enrichment agent.
 */
public final class KafkaTicketAgentRunner implements Closeable {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
    private static final int SINGLE_RUN_ASSIGNMENT_ATTEMPTS = 3;
    private static final Logger LOGGER = Logger.getLogger(KafkaTicketAgentRunner.class.getName());

    private final KafkaConsumer<String, String> consumer;
    private final KafkaProducer<String, String> producer;
    private final EventEnrichmentAgent agent;
    private final KafkaTicketTopics topics;
    private final ObjectMapper objectMapper;

    /**
     * Creates a runner with fully constructed Kafka clients and the enrichment agent.
     * The constructor subscribes the consumer immediately so the instance is ready to poll as soon
     * as it is created.
     *
     * @param consumer Kafka consumer that reads raw support-ticket events
     * @param producer Kafka producer that publishes enriched events
     * @param agent application-level enrichment agent used for each consumed ticket
     * @param topics input topic, output topic, and consumer group configuration
     * @param objectMapper mapper used to deserialize raw events and serialize enriched ones
     */
    public KafkaTicketAgentRunner(
            KafkaConsumer<String, String> consumer,
            KafkaProducer<String, String> producer,
            EventEnrichmentAgent agent,
            KafkaTicketTopics topics,
            ObjectMapper objectMapper
    ) {
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.producer = Objects.requireNonNull(producer, "producer");
        this.agent = Objects.requireNonNull(agent, "agent");
        this.topics = Objects.requireNonNull(topics, "topics");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.consumer.subscribe(List.of(topics.inputTopic()));
        LOGGER.info(() -> "Kafka consumer subscribed to input topic '%s'. Enriched events will be published to topic '%s'."
                .formatted(topics.inputTopic(), topics.outputTopic()));
    }

    /**
     * Builds a runner using default Kafka client settings that fit this PoC: string keys and
     * values, manual offset commits, and `earliest` offset reset for new consumer groups.
     *
     * @param bootstrapServers Kafka bootstrap servers, for example `localhost:9092`
     * @param topics input topic, output topic, and group id used by the pipeline
     * @param agent application-level enrichment agent that processes each consumed ticket
     * @return fully configured runner ready to consume and publish events
     */
    public static KafkaTicketAgentRunner createDefault(
            String bootstrapServers,
            KafkaTicketTopics topics,
            EventEnrichmentAgent agent
    ) {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers");
        Objects.requireNonNull(topics, "topics");
        Objects.requireNonNull(agent, "agent");

        Properties consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, topics.groupId());
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");

        return new KafkaTicketAgentRunner(
                new KafkaConsumer<>(consumerProperties),
                new KafkaProducer<>(producerProperties),
                agent,
                topics,
                new ObjectMapper().registerModule(new JavaTimeModule())
        );
    }

    /**
     * Starts continuous polling mode. The runner blocks the current thread, repeatedly polls Kafka,
     * enriches any received tickets, publishes results to the output topic, and commits offsets
     * after successful processing.
     */
    public void run() {
        while (true) {
            pollAndProcess();
        }
    }

    /**
     * Executes a bounded polling cycle intended for demos and local verification. The runner makes
     * a few poll attempts so the consumer has time to join the group and receive an assignment
     * before deciding whether the topic is empty.
     */
    public void runOnce() {
        ConsumerRecords<String, String> records = pollForSingleRun();
        processRecords(records);
    }

    private void pollAndProcess() {
        ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
        processRecords(records);
    }

    private ConsumerRecords<String, String> pollForSingleRun() {
        for (int attempt = 1; attempt <= SINGLE_RUN_ASSIGNMENT_ATTEMPTS; attempt++) {
            ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
            if (!records.isEmpty()) {
                LOGGER.info("Received records during single-run poll attempt %d.".formatted(attempt));
                return records;
            }

            if (!consumer.assignment().isEmpty()) {
                LOGGER.info("Consumer assignment is ready after single-run poll attempt %d, but no records were returned yet."
                        .formatted(attempt));
            } else {
                LOGGER.info("Consumer assignment is not ready yet after single-run poll attempt %d. Polling again."
                        .formatted(attempt));
            }
        }

        return ConsumerRecords.empty();
    }

    private void processRecords(ConsumerRecords<String, String> records) {
        if (records.isEmpty()) {
            LOGGER.info(() -> "No records received from topic '%s' during this poll cycle."
                    .formatted(topics.inputTopic()));
            return;
        }

        for (ConsumerRecord<String, String> record : records) {
            LOGGER.info(() -> "Received raw Kafka event from topic '%s' with key '%s':%n%s"
                    .formatted(
                            topics.inputTopic(),
                            record.key(),
                            prettyPrintJson(record.value())
                    ));

            SupportTicketEvent ticket = readTicket(record.value());
            LOGGER.info(() -> "Received support ticket event from topic '%s' with key '%s' and ticketId '%s'."
                    .formatted(topics.inputTopic(), record.key(), ticket.ticketId()));

            EnrichedSupportTicketEvent enrichedTicket = agent.process(ticket);
            String enrichedPayload = writeEnrichedTicket(enrichedTicket);
            producer.send(new ProducerRecord<>(
                    topics.outputTopic(),
                    ticket.ticketId(),
                    enrichedPayload
            ));
            LOGGER.info(() -> "Published enriched ticket event to topic '%s' with key '%s', category '%s', and priority '%s'."
                    .formatted(
                            topics.outputTopic(),
                            ticket.ticketId(),
                            enrichedTicket.enrichment().category(),
                            enrichedTicket.enrichment().priority()
                    ));
        }

        producer.flush();
        consumer.commitSync();
        LOGGER.info(() -> "Committed Kafka offsets after processing %d record(s).".formatted(records.count()));
    }

    private SupportTicketEvent readTicket(String value) {
        try {
            return objectMapper.readValue(value, SupportTicketEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not deserialize SupportTicketEvent: " + value, exception);
        }
    }

    private String writeEnrichedTicket(EnrichedSupportTicketEvent enrichedTicket) {
        try {
            return objectMapper.writeValueAsString(enrichedTicket);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize EnrichedSupportTicketEvent", exception);
        }
    }

    private String prettyPrintJson(String value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objectMapper.readTree(value));
        } catch (JsonProcessingException exception) {
            return value;
        }
    }

    /**
     * Closes the Kafka consumer and producer owned by this runner.
     * This method is called automatically when the runner is used in a try-with-resources block.
     */
    @Override
    public void close() {
        consumer.close();
        producer.close();
    }
}

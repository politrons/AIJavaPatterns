package com.example.kafkallmagent.messaging;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Creates the input and output topics used by the PoC when they do not exist yet.
 */
public final class KafkaTopicBootstrapper {

    private static final Logger LOGGER = Logger.getLogger(KafkaTopicBootstrapper.class.getName());
    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(10);
    private static final int ADMIN_TIMEOUT_MS = (int) ADMIN_TIMEOUT.toMillis();
    private static final int DEFAULT_PARTITIONS = 1;
    private static final short DEFAULT_REPLICATION_FACTOR = 1;

    /**
     * Ensures that the raw input topic and enriched output topic required by the PoC exist on the
     * target Kafka cluster. Missing topics are created with a single partition and replication
     * factor one so the local demo can run on a single broker.
     *
     * @param bootstrapServers Kafka bootstrap servers used to connect the admin client
     * @param topics topic names that must exist before producing or consuming messages
     * @throws IllegalStateException if Kafka cannot be reached or topic creation cannot complete
     */
    public void ensureTopicsExist(String bootstrapServers, KafkaTicketTopics topics) {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers");
        Objects.requireNonNull(topics, "topics");

        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, ADMIN_TIMEOUT_MS);
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, ADMIN_TIMEOUT_MS);

        LOGGER.info(() -> "Checking Kafka connectivity and ensuring topics exist on bootstrapServers '%s'."
                .formatted(bootstrapServers));

        try (Admin admin = Admin.create(properties)) {
            Set<String> existingTopics = admin.listTopics().names().get(ADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            createTopicIfMissing(admin, existingTopics, topics.inputTopic());
            createTopicIfMissing(admin, existingTopics, topics.outputTopic());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while ensuring Kafka topics exist", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not ensure Kafka topics exist", exception);
        }
    }

    private void createTopicIfMissing(Admin admin, Set<String> existingTopics, String topicName)
            throws ExecutionException, InterruptedException, TimeoutException {
        if (existingTopics.contains(topicName)) {
            LOGGER.info(() -> "Kafka topic '%s' already exists.".formatted(topicName));
            return;
        }

        NewTopic newTopic = new NewTopic(topicName, DEFAULT_PARTITIONS, DEFAULT_REPLICATION_FACTOR);
        LOGGER.info(() -> "Creating Kafka topic '%s'.".formatted(topicName));
        admin.createTopics(List.of(newTopic)).all().get();
        admin.describeTopics(List.of(topicName)).allTopicNames().get(ADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        LOGGER.info(() -> "Created Kafka topic '%s' with %d partition(s) and replication factor %d."
                .formatted(topicName, DEFAULT_PARTITIONS, (int) DEFAULT_REPLICATION_FACTOR));
    }
}

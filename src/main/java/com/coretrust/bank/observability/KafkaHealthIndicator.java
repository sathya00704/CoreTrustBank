package com.coretrust.bank.observability;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Shows up as "kafka" in /actuator/health. Not part of readiness: the outbox absorbs Kafka outages. */
@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_MS = 3000;

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, TIMEOUT_MS);

        AdminClient client = AdminClient.create(config);
        try {
            DescribeClusterResult cluster = client.describeCluster();
            String clusterId = cluster.clusterId().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            int brokers = cluster.nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size();
            return Health.up().withDetail("clusterId", clusterId).withDetail("brokers", brokers).build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("error", "interrupted").build();
        } catch (Exception ex) {
            return Health.down().withDetail("error", ex.getMessage()).build();
        } finally {
            client.close(Duration.ofSeconds(1));
        }
    }
}
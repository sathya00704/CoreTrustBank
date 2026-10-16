package com.coretrust.bank.config;

import com.coretrust.bank.messaging.EventType;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Spring creates these topics at startup if they don't exist. */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic transfersTopic() {
        return TopicBuilder.name(EventType.TOPIC_TRANSFERS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic accountsTopic() {
        return TopicBuilder.name(EventType.TOPIC_ACCOUNTS).partitions(3).replicas(1).build();
    }

    /** Dead-letter topics: same partition count, because failed records keep their partition. */
    @Bean
    public NewTopic transfersDeadLetterTopic() {
        return TopicBuilder.name(EventType.TOPIC_TRANSFERS + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic accountsDeadLetterTopic() {
        return TopicBuilder.name(EventType.TOPIC_ACCOUNTS + ".dlt").partitions(3).replicas(1).build();
    }
}
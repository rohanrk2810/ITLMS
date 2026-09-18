package com.itilms.common.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import com.itilms.common.event.KafkaTopics;

/**
 * Creates every IT-ILMS topic, with a deliberate partition count, at startup.
 *
 * <p>Left to broker auto-creation, a topic comes into existence the first time
 * anything touches it, with the broker's default of a single partition. One
 * partition means one consumer thread per service no matter how many instances
 * run, and repartitioning later reshuffles which instance owns which key. Doing
 * it here fixes the count up front.
 *
 * <p>The list is read from {@link KafkaTopics} itself rather than repeated, so
 * adding a constant there is all it takes to create a topic. Every service runs
 * this; creation is idempotent, and existing topics are left untouched.
 */
@Configuration
@ConditionalOnProperty(prefix = "itilms.kafka", name = "create-topics", havingValue = "true", matchIfMissing = true)
public class KafkaTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics itilmsTopics(
            @Value("${itilms.kafka.partitions:3}") int partitions,
            @Value("${itilms.kafka.replicas:1}") int replicas) {

        NewTopic[] topics = Arrays.stream(KafkaTopics.class.getDeclaredFields())
                .filter(KafkaTopicsConfig::isTopicConstant)
                .map(field -> TopicBuilder.name(read(field))
                        .partitions(partitions)
                        .replicas(replicas)
                        .build())
                .toArray(NewTopic[]::new);

        return new KafkaAdmin.NewTopics(topics);
    }

    private static boolean isTopicConstant(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
                && Modifier.isFinal(modifiers) && field.getType() == String.class;
    }

    private static String read(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot read topic constant " + field.getName(), ex);
        }
    }
}

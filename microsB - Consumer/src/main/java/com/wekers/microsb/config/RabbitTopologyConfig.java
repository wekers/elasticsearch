package com.wekers.microsb.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitTopologyConfig {

    private final RabbitMQProperties properties;

    // ============================================================
    // EXCHANGES
    // ============================================================
    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(properties.getExchanges().getMain(), true, false);
    }

    @Bean
    public DirectExchange productDlx() {
        return new DirectExchange(properties.getExchanges().getDlx(), true, false);
    }

    // ============================================================
    // QUEUES
    // ============================================================
    @Bean
    public Queue createdQueue() {
        return QueueBuilder.durable(properties.getQueues().getCreated()).build();
    }

    @Bean
    public Queue updatedQueue() {
        return QueueBuilder.durable(properties.getQueues().getUpdated()).build();
    }

    @Bean
    public Queue deletedQueue() {
        return QueueBuilder.durable(properties.getQueues().getDeleted()).build();
    }

    @Bean
    public Queue retryQueue() {
        return QueueBuilder.durable(properties.getQueues().getRetry5s())
                .withArgument("x-message-ttl", properties.getRetry().getRetryDelayMs())
                .withArgument("x-dead-letter-exchange", properties.getExchanges().getMain())
                .withArgument("x-dead-letter-routing-key", properties.getRoutingKeys().getCreated())
                .build();
    }

    @Bean
    public Queue deadQueue() {
        return QueueBuilder.durable(properties.getQueues().getDead()).build();
    }

    // ============================================================
    // BINDINGS
    // ============================================================

    // CREATE events
    @Bean
    public Binding bindCreated() {
        return BindingBuilder.bind(createdQueue())
                .to(productExchange())
                .with(properties.getRoutingKeys().getCreated());
    }

    // UPDATE events
    @Bean
    public Binding bindUpdated() {
        return BindingBuilder.bind(updatedQueue())
                .to(productExchange())
                .with(properties.getRoutingKeys().getUpdated());
    }

    // DELETED events
    @Bean
    public Binding bindDeleted() {
        return BindingBuilder.bind(deletedQueue())
                .to(productExchange())
                .with(properties.getRoutingKeys().getDeleted());
    }

    // RETRY
    @Bean
    public Binding bindRetry() {
        return BindingBuilder.bind(retryQueue())
                .to(productDlx())
                .with(properties.getRoutingKeys().getRetry5s());
    }

    // DLQ
    @Bean
    public Binding bindDead() {
        return BindingBuilder.bind(deadQueue())
                .to(productDlx())
                .with(properties.getRoutingKeys().getDead());
    }
}

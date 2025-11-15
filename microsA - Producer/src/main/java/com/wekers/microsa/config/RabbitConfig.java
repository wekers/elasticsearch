package com.wekers.microsa.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    private final RabbitMQProperties properties;

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(properties.getExchanges().getMain(), true, false);
    }

    @Bean
    public DirectExchange productDlx() {
        return new DirectExchange(properties.getExchanges().getDlx(), true, false);
    }

    @Bean
    public Queue mainQueue() {
        return QueueBuilder.durable(properties.getQueues().getMain())
                .withArgument("x-dead-letter-exchange", properties.getExchanges().getDlx())
                .withArgument("x-dead-letter-routing-key", properties.getRoutingKeys().getRetry5s())
                .build();
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
        return QueueBuilder.durable(properties.getQueues().getDead())
                .build();
    }

    @Bean
    public Queue deletedQueue() {
        return QueueBuilder.durable(properties.getQueues().getDeleted())
                .build();
    }

    @Bean
    public Binding bindMain() {
        return BindingBuilder.bind(mainQueue())
                .to(productExchange())
                .with(properties.getRoutingKeys().getCreated());
    }

    @Bean
    public Binding bindRetry() {
        return BindingBuilder.bind(retryQueue())
                .to(productDlx())
                .with(properties.getRoutingKeys().getRetry5s());
    }

    @Bean
    public Binding bindDead() {
        return BindingBuilder.bind(deadQueue())
                .to(productDlx())
                .with(properties.getRoutingKeys().getDead());
    }

    @Bean
    public Binding bindDeleted() {
        return BindingBuilder.bind(deletedQueue())
                .to(productExchange())
                .with(properties.getRoutingKeys().getDeleted());
    }

    @Bean
    public Binding bindUpdated() {
        return BindingBuilder.bind(mainQueue())
                .to(productExchange())
                .with(properties.getRoutingKeys().getUpdated());
    }

}

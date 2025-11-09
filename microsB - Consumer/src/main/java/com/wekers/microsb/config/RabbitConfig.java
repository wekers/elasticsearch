package com.wekers.microsb.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    private final RabbitMQProperties properties;

    @Bean
    TopicExchange productsExchange() {
        return new TopicExchange(properties.getExchanges().getMain(), true, false);
    }

    @Bean
    DirectExchange productsDlx() {
        return new DirectExchange(properties.getExchanges().getDlx(), true, false);
    }

    @Bean
    Queue productsQueue() {
        return QueueBuilder.durable(properties.getQueues().getMain())
                .withArgument("x-dead-letter-exchange", properties.getExchanges().getDlx())
                .withArgument("x-dead-letter-routing-key", properties.getRoutingKeys().getRetry5s())
                .build();
    }

    @Bean
    Queue retry5sQueue() {
        return QueueBuilder.durable(properties.getQueues().getRetry5s())
                .withArgument("x-message-ttl", properties.getRetry().getRetryDelayMs())
                .withArgument("x-dead-letter-exchange", properties.getExchanges().getMain())
                .withArgument("x-dead-letter-routing-key", properties.getRoutingKeys().getCreated())
                .build();
    }

    @Bean
    Queue deadQueue() {
        return QueueBuilder.durable(properties.getQueues().getDead()).build();
    }

    @Bean
    Binding bindMain() {
        return BindingBuilder.bind(productsQueue())
                .to(productsExchange())
                .with(properties.getRoutingKeys().getCreated());
    }

    @Bean
    Binding bindRetry() {
        return BindingBuilder.bind(retry5sQueue())
                .to(productsDlx())
                .with(properties.getRoutingKeys().getRetry5s());
    }

    @Bean
    Binding bindDead() {
        return BindingBuilder.bind(deadQueue())
                .to(productsDlx())
                .with(properties.getRoutingKeys().getDead());
    }

    @Bean
    public SimpleRabbitListenerContainerFactory manualAckFactory(ConnectionFactory cf,
                                                                 Jackson2JsonMessageConverter converter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(50);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        factory.setMessageConverter(converter);
        return factory;
    }
}
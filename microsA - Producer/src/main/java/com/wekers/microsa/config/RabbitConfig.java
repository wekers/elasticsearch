package com.wekers.microsa.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "products.exchange";
    public static final String DLX = "products.dlx";
    public static final String RK_CREATED = "products.created";
    public static final String RK_RETRY_5S = "products.retry.5s";
    public static final String Q_MAIN = "products.queue";

    @Bean
    public Queue productQueue(){
        return QueueBuilder.durable(Q_MAIN)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", RK_RETRY_5S)
                .build();
    }

    @Bean
    public TopicExchange productExchange(){
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange productDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Binding binding(Queue productQueue, TopicExchange productExchange){
        return BindingBuilder
                .bind(productQueue)
                .to(productExchange)
                .with(RK_CREATED);
    }

    // Opcional: Criar também as outras filas para garantir consistência
    @Bean
    public Queue retry5sQueue() {
        return QueueBuilder.durable("products.retry.5s.queue")
                .withArgument("x-message-ttl", 5000) // 5 segundos
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RK_CREATED)
                .build();
    }

    @Bean
    public Queue deadQueue() {
        return QueueBuilder.durable("products.dead.queue").build();
    }

    @Bean
    public Binding bindRetry(DirectExchange productDlx, Queue retry5sQueue) {
        return BindingBuilder.bind(retry5sQueue)
                .to(productDlx)
                .with(RK_RETRY_5S);
    }

    @Bean
    public Binding bindDead(DirectExchange productDlx, Queue deadQueue) {
        return BindingBuilder.bind(deadQueue)
                .to(productDlx)
                .with("products.dead");
    }
}
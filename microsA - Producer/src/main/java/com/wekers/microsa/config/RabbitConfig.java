package com.wekers.microsa.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public Queue productQueue(){
        return new Queue("products.queue", true, false, false);
    }

    @Bean
    public TopicExchange productExchange(){
        return new TopicExchange("products.exchange", true, false);
    }

    @Bean
    public Binding binding(Queue productQueue, TopicExchange productExchange){
        return BindingBuilder
                .bind(productQueue)
                .to(productExchange)
                .with("products.created");
    }
}

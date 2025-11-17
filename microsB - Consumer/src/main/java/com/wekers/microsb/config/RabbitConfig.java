package com.wekers.microsb.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.SimpleMessageConverter;

@Configuration
public class RabbitConfig {

    @Bean
    public SimpleRabbitListenerContainerFactory manualAckFactory(
            ConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // não tenta usar Jackson2MessageConverter, usa texto/byte puro
        factory.setMessageConverter(new SimpleMessageConverter());
        return factory;
    }
}

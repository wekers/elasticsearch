package com.wekers.microsb.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public SimpleRabbitListenerContainerFactory manualAckFactory(
            ConnectionFactory cf
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        // FORÇA O RABBIT A NÃO USAR JACKSON E NÃO USAR TYPEID
        factory.setMessageConverter(new org.springframework.amqp.support.converter.SimpleMessageConverter());

        return factory;
    }
}


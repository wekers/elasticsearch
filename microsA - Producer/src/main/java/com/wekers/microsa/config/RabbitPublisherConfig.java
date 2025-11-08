package com.wekers.microsa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitPublisherConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitPublisherConfig.class);

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter(); // Converte objetos Java para JSON
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter conv) {
        RabbitTemplate rt = new RabbitTemplate(cf);
        rt.setMessageConverter(conv);
        rt.setMandatory(true); // Mensagens devem ser roteadas

        // CONFIRMAÇÃO DO BROKER - Garante que a mensagem foi recebida pelo RabbitMQ
        rt.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("❌ NACK do broker: {}", cause);
            }
        });

        // RETORNO DE MENSAGENS - Se a mensagem não puder ser roteada
        rt.setReturnsCallback(returned ->
                log.error("⚠\uFE0F Mensagem devolvida: {}", returned.getReplyText())
        );

        return rt;
    }
}

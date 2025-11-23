package com.wekers.microsa.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Slf4j
@Configuration
public class RabbitQueueAvailable {

    private final ConnectionFactory connectionFactory;
    private final RabbitMQProperties properties;

    private final boolean queueAvailable;

    // Verificação acontece uma única vez no construtor
    public RabbitQueueAvailable(ConnectionFactory connectionFactory, RabbitMQProperties properties) {
        this.connectionFactory = connectionFactory;
        this.properties = properties;
        this.queueAvailable = checkQueueAvailability();
    }

    public boolean isMicroserviceBQueueAvailable() {
        return queueAvailable; // ← Retorna resultado em memória
    }

    private boolean checkQueueAvailability() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);

        try {
            String microserviceBQueueName = properties.getRoutingKeys().getCreated() + ".queue";

            Properties queueProps = admin.getQueueProperties(microserviceBQueueName);

            if (queueProps != null) {
                log.info("✅ Microservice B queue '{}' is available", microserviceBQueueName);
                return true;
            } else {
                log.error("❌ Microservice B queue '{}' not found", microserviceBQueueName);
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Cannot connect to RabbitMQ: {}", e.getMessage());
            return false;
        }
    }
}
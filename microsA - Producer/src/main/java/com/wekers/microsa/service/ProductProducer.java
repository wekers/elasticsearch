package com.wekers.microsa.service;

import com.wekers.microsa.config.RabbitMQProperties;
import com.wekers.microsa.dto.ProductCreatedEvent;
import com.wekers.microsa.dto.ProductDeletedEvent;
import com.wekers.microsa.dto.ProductUpdatedEvent;
import com.wekers.microsa.entity.ProductEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductProducer {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties properties;

    // ======================================================
    // CREATE
    // ======================================================
    public void sendCreated(ProductEntity entity) {
        ProductCreatedEvent evt = new ProductCreatedEvent(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice()
        );

        rabbitTemplate.convertAndSend(
                properties.getExchanges().getMain(),
                properties.getRoutingKeys().getCreated(),
                evt // envia objeto puro, converter faz JSON
        );

        log.info("📤 Sent CREATED event to RabbitMQ: id={}", entity.getId());
    }

    // ======================================================
    // UPDATE
    // ======================================================
    public void sendUpdated(ProductEntity entity) {
        ProductUpdatedEvent evt = new ProductUpdatedEvent(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice()
        );

        rabbitTemplate.convertAndSend(
                properties.getExchanges().getMain(),
                properties.getRoutingKeys().getUpdated(),
                evt
        );

        log.info("📤 Sent UPDATED event to RabbitMQ: id={}", entity.getId());
    }

    // ======================================================
    // DELETE
    // ======================================================
    public void sendDeleted(ProductEntity entity) {
        ProductDeletedEvent evt = new ProductDeletedEvent(
                entity.getId(),
                entity.getName(),
                entity.getDescription()
        );

        rabbitTemplate.convertAndSend(
                properties.getExchanges().getMain(),
                properties.getRoutingKeys().getDeleted(),
                evt
        );

        log.info("📤 Sent DELETED event to RabbitMQ: id={}", entity.getId());
    }
}

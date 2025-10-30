package com.wekers.microsa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wekers.microsa.dto.ProductCreatedEvent;
import com.wekers.microsa.entity.ProductEntity;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public ProductProducer(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(ProductEntity entity) {
        try {
            ProductCreatedEvent doc = new ProductCreatedEvent();
            doc.setId(entity.getId());
            doc.setName(entity.getName());
            doc.setDescription(entity.getDescription());
            doc.setPrice(entity.getPrice());

            String json = objectMapper.writeValueAsString(doc);

            rabbitTemplate.convertAndSend("products.exchange", "products.created", json);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao publicar evento", e);
        }
    }

}

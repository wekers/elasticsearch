package com.wekers.microsb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wekers.microsb.dto.ProductCreatedEvent;
import com.wekers.microsb.document.ProductDocument;
import com.wekers.microsb.repository.ProductEsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class ProductConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductConsumer.class);

    private final ProductEsRepository esRepository;
    private final ObjectMapper objectMapper;

    public ProductConsumer(ProductEsRepository esRepository, ObjectMapper objectMapper){
        this.esRepository = esRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "products.queue")
    public void receive(String messageJson) throws Exception {

        // 1) Converte JSON para o **contrato do evento**, não para ProductDocument
        ProductCreatedEvent event = objectMapper.readValue(messageJson, ProductCreatedEvent.class);

        // 2) Constrói o documento de Elasticsearch internamente
        ProductDocument doc = new ProductDocument();
        doc.setId(event.getId().toString());
        doc.setName(event.getName());
        doc.setDescription(event.getDescription());
        doc.setPrice(event.getPrice());
        doc.setNameSpell(doc.getName());

        // 3) Indexa no ES
        esRepository.save(doc);

        log.info("Indexado no Elasticsearch: {}", doc.getName());
    }
}

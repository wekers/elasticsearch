package com.wekers.microsb.service.handlers;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wekers.microsb.config.RabbitMQProperties;
import com.wekers.microsb.document.ProductDocument;
import com.wekers.microsb.repository.ProductEsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductCreatedHandler extends ProductBaseHandler {

    private final ElasticsearchClient client;
    private final ProductEsRepository repository;

    public ProductCreatedHandler(RabbitTemplate rabbitTemplate,
                                 RabbitMQProperties properties,
                                 ObjectMapper objectMapper,
                                 ElasticsearchClient client,
                                 ProductEsRepository repository) {
        super(rabbitTemplate, properties, objectMapper);
        this.client = client;
        this.repository = repository;
    }

    public void processCreate(ProductDocument doc) throws Exception {
        // normalizar helpers
        doc.rebuildUniqueKey();
        if (doc.getNameSpell() == null) {
            doc.setNameSpell(doc.getName());
        }

        // DUP por ID
        if (repository.existsById(doc.getId())) {
            log.warn("⚠ CREATE ignored — id already exists: {}", doc.getId());
            return;
        }

        // DUP por uniqueKey
        if (repository.existsByUniqueKey(doc.getUniqueKey())) {
            log.warn("⚠ CREATE ignored — duplicate uniqueKey: {}", doc.getUniqueKey());
            return;
        }

        IndexResponse r = client.index(i -> i
                .index("products_write")
                .id(doc.getId())
                .opType(OpType.Create)
                .document(doc)
        );

        log.info("🟢 CREATED in ES: id={} result={} version={}",
                doc.getId(), r.result(), r.version());
    }
}

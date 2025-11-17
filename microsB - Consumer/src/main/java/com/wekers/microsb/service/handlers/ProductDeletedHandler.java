package com.wekers.microsb.service.handlers;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wekers.microsb.config.RabbitMQProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductDeletedHandler extends ProductBaseHandler {

    private final ElasticsearchClient client;

    public ProductDeletedHandler(RabbitTemplate rabbitTemplate,
                                 RabbitMQProperties properties,
                                 ObjectMapper objectMapper,
                                 ElasticsearchClient client) {
        super(rabbitTemplate, properties, objectMapper);
        this.client = client;
    }

    public void processDelete(String id) throws Exception {

        DeleteResponse resp = client.delete(d -> d
                .index("products_write")
                .id(id)
        );
        // Como DELETE não deve ser reprocessado (não faz sentido duplicar DEL),
        // enviamos direto a DLQ:
        if ("not_found".equals(resp.result().jsonValue())) {
            log.warn("⚠ DELETE ignored — not found id={}", id);
            return;
        }

        log.info("🗑️ DELETED from ES: {}", id);
    }
}

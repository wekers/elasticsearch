package com.wekers.microsb.service.handlers;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.GetResponse;
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
public class ProductUpdatedHandler extends ProductBaseHandler {

    private final ElasticsearchClient client;
    private final ProductEsRepository repository;

    public ProductUpdatedHandler(RabbitTemplate rabbitTemplate,
                                 RabbitMQProperties properties,
                                 ObjectMapper objectMapper,
                                 ElasticsearchClient client,
                                 ProductEsRepository repository) {
        super(rabbitTemplate, properties, objectMapper);
        this.client = client;
        this.repository = repository;
    }

    public void processUpdateOptimistic(ProductDocument incoming) throws Exception {
        incoming.rebuildUniqueKey();
        if (incoming.getNameSpell() == null) {
            incoming.setNameSpell(incoming.getName());
        }

        GetResponse<ProductDocument> current = client.get(
                g -> g.index("products_write").id(incoming.getId()),
                ProductDocument.class
        );

        if (!current.found()) {
            log.warn("⚠ UPDATE: document not found, fallback to CREATE");

            IndexResponse created = client.index(i -> i
                    .index("products_write")
                    .id(incoming.getId())
                    .document(incoming)
            );

            log.info("🟢 CREATED from UPDATE fallback: id={} version={}",
                    incoming.getId(), created.version());
            return;
        }

        ProductDocument existing = current.source();
        // aplica mudanças
        existing.setName(incoming.getName());
        existing.setDescription(incoming.getDescription());
        existing.setPrice(incoming.getPrice());
        existing.rebuildUniqueKey();

        long seqNo = current.seqNo();
        long primaryTerm = current.primaryTerm();

        try {
            IndexResponse updateResp = client.index(i -> i
                    .index("products_write")
                    .id(existing.getId())
                    .ifSeqNo(seqNo)
                    .ifPrimaryTerm(primaryTerm)
                    .document(existing)
            );

            log.info("🟢 UPDATED OK: id={} version={}", existing.getId(), updateResp.version());
        } catch (ElasticsearchException lockEx) {
            if (lockEx.getMessage() != null
                    && lockEx.getMessage().contains("version_conflict_engine_exception")) {
                log.warn("🔒 Version conflict — another update won, letting retry policy handle");
                throw lockEx;
            }
            throw lockEx;
        }
    }
}

package com.wekers.microsb.service;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.wekers.microsb.config.RabbitMQProperties;
import com.wekers.microsb.document.ProductDocument;
import com.wekers.microsb.repository.ProductEsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;


import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductConsumer {

    private final ProductEsRepository esRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties properties;
    private final ElasticsearchClient client;

    @RabbitListener(
            queues = "${app.rabbitmq.queues.main}",
            containerFactory = "manualAckFactory"
    )
    public void receive(Message msg, Channel channel) throws IOException {
        long tag = msg.getMessageProperties().getDeliveryTag();

        try {
            // ============================================================
            // 1) Deserialize + sempre corrigir campos calculados
            // ============================================================
            ProductDocument doc =
                    objectMapper.readValue(msg.getBody(), ProductDocument.class);

            // sempre gerar UNIQUE_KEY
            doc.rebuildUniqueKey();

            // garantir nameSpell preenchido
            if (doc.getNameSpell() == null) {
                doc.setNameSpell(doc.getName());
            }

            // ============================================================
            // 2) Verificar duplicidade por ID
            // ============================================================
            boolean existsId = esRepository.existsById(doc.getId());

            // ============================================================
            // 3) Verificar duplicidade por uniqueKey
            // ============================================================
            boolean existsKey = esRepository.existsByUniqueKey(doc.getUniqueKey());

            if (existsId && existsKey) {
                log.warn(" Duplicate (ID + UNIQUE_KEY) ignored → {}", doc.getUniqueKey());
                channel.basicAck(tag, false);
                return;
            }

            // ============================================================
            // 4) CREATE → somente se ID não existe
            // ============================================================
            if (!existsId) {
                IndexResponse response = client.index(i -> i
                        .index("products_write")
                        .id(doc.getId())
                        .opType(OpType.Create) // create-only
                        .document(doc)
                );

                log.info("Created in ES: id={} result={}", doc.getId(), response.result());
                channel.basicAck(tag, false);
                return;
            }

            // ============================================================
            // 5) UPDATE — existe ID → fazer update com Optimistic locking
            // ============================================================
            // 1) Buscar documento atual
            GetResponse<ProductDocument> current = client.get(
                    g -> g.index("products_write").id(doc.getId()),
                    ProductDocument.class
            );

            if (!current.found()) {
                log.warn("Document disappeared during update — fallback CREATE");
                client.index(i -> i
                        .index("products_write")
                        .id(doc.getId())
                        .opType(OpType.Create)
                        .document(doc)
                );
                channel.basicAck(tag, false);
                return;
            }

            long seqNo = current.seqNo();
            long primaryTerm = current.primaryTerm();

            // 2) UPDATE via index (optimistic locking)
            try {
                IndexResponse update = client.index(i -> i
                        .index("products_write")
                        .id(doc.getId())
                        .ifSeqNo(seqNo)
                        .ifPrimaryTerm(primaryTerm)
                        .document(doc)
                );

                log.info("Updated OK: id={} version={} result={}", doc.getId(),
                        update.version(), update.result());
                channel.basicAck(tag, false);
                return;
            }
            catch (ElasticsearchException lockEx) {

                // Outro processo gravou no ES antes, conflito → tentar novamente
                if (lockEx.getMessage().contains("version_conflict_engine_exception")) {
                    log.warn("Version conflict — retrying message");
                    retryMessage(msg, channel, tag);
                    return;
                }

                throw lockEx;
            }

        }
        catch (Exception e) {
            log.error("Error processing event", e);

            retryMessage(msg, channel, tag);
        }
    }

    // ============================================================
    // REPROCESSAMENTO COM CONTROLE DE RETENTATIVAS
    // ============================================================
    private void retryMessage(Message msg, Channel channel, long tag) throws IOException {

        int retry = (int) msg.getMessageProperties()
                .getHeaders()
                .getOrDefault("x-retry-count", 0);

        if (retry >= properties.getRetry().getMaxAttempts()) {
            log.error("Max retries reached — sending to DLQ");

            rabbitTemplate.convertAndSend(
                    properties.getExchanges().getDlx(),
                    properties.getRoutingKeys().getDead(),
                    new Message(msg.getBody(), copyHeaders(msg, false))
            );

            channel.basicAck(tag, false);
            return;
        }

        // Incrementa retry
        MessageProperties props = copyHeaders(msg, true);
        props.setHeader("x-retry-count", retry + 1);

        // Envia para fila de retry
        rabbitTemplate.send(
                properties.getExchanges().getDlx(),
                properties.getRoutingKeys().getRetry5s(),
                new Message(msg.getBody(), props)
        );

        channel.basicAck(tag, false);
    }

    // ============================================================
    // Copiar headers corretamente
    // ============================================================
    private MessageProperties copyHeaders(Message original, boolean keepRetryHeader) {
        MessageProperties props = new MessageProperties();
        props.getHeaders().putAll(original.getMessageProperties().getHeaders());

        if (!keepRetryHeader)
            props.getHeaders().remove("x-retry-count");

        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(original.getMessageProperties().getContentEncoding());
        return props;
    }
}

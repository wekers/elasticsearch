package com.wekers.microsb.service.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.wekers.microsb.document.ProductDocument;
import com.wekers.microsb.service.handlers.ProductCreatedHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductConsumerCreatedListener {

    private final ObjectMapper objectMapper;
    private final ProductCreatedHandler handler;

    @RabbitListener(
            queues = "${app.rabbitmq.queues.created}",
            containerFactory = "manualAckFactory"
    )
    public void onCreated(Message msg, Channel channel) throws IOException {
        long tag = msg.getMessageProperties().getDeliveryTag();
        try {
            ProductDocument doc =
                    objectMapper.readValue(msg.getBody(), ProductDocument.class);

            handler.processCreate(doc);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("❌ Error processing CREATED event", e);
            handler.retryOrDlq(msg, channel, tag, e);
        }
    }
}

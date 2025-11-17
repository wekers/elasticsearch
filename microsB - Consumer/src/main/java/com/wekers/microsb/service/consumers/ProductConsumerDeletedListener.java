package com.wekers.microsb.service.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.wekers.microsb.dto.ProductDeletedEvent;
import com.wekers.microsb.service.handlers.ProductDeletedHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductConsumerDeletedListener {

    private final ObjectMapper objectMapper;
    private final ProductDeletedHandler handler;

    @RabbitListener(
            queues = "${app.rabbitmq.queues.deleted}",
            containerFactory = "manualAckFactory"
    )
    public void onDeleted(Message msg, Channel channel) throws IOException {
        long tag = msg.getMessageProperties().getDeliveryTag();

        try {
            ProductDeletedEvent event =
                    objectMapper.readValue(msg.getBody(), ProductDeletedEvent.class);

            handler.processDelete(event.id().toString());
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("❌ Error processing DELETED event", e);
            handler.retryOrDlq(msg, channel, tag, e);
        }
    }
}

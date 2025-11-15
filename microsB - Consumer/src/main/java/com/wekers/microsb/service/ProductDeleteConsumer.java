package com.wekers.microsb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.wekers.microsb.dto.ProductDeletedEvent;
import com.wekers.microsb.repository.ProductEsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductDeleteConsumer {

    private final ProductEsRepository esRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(
            queues = "${app.rabbitmq.queues.deleted}",
            containerFactory = "manualAckFactory"
    )
    public void receiveDelete(Message msg, Channel channel) throws IOException {
        long tag = msg.getMessageProperties().getDeliveryTag();

        try {
            ProductDeletedEvent event =
                    objectMapper.readValue(msg.getBody(), ProductDeletedEvent.class);

            esRepository.deleteById(String.valueOf(event.id()));

            channel.basicAck(tag, false);
            log.info("🗑️ Deleted from ES: {}", event.id());

        } catch (Exception ex) {
            log.error("❌ Error processing DELETE event", ex);
            channel.basicNack(tag, false, false);
        }
    }
}


package com.wekers.microsb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.wekers.microsb.config.RabbitMQConstants;
import com.wekers.microsb.config.RabbitMQProperties;
import com.wekers.microsb.document.ProductDocument;
import com.wekers.microsb.repository.ProductEsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductConsumer {

    private final ProductEsRepository esRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Jackson2JsonMessageConverter messageConverter;
    private final RabbitMQProperties properties;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queues.main}", containerFactory = "manualAckFactory")
    public void receive(Message msg, Channel channel) throws IOException {
        long tag = msg.getMessageProperties().getDeliveryTag();

        try {
            ProductDocument doc = convertToProductDocument(msg);
            esRepository.save(doc);
            channel.basicAck(tag, false);
            log.info("✅ Product processed successfully: {}", doc.getId());

        } catch (Exception ex) {
            log.error("❌ Error processing message", ex);
            handleProcessingFailure(msg, channel, tag, ex);
        }
    }

    private ProductDocument convertToProductDocument(Message message) throws IOException {
        try {
            // Tenta converter usando o messageConverter
            Object converted = messageConverter.fromMessage(message);

            if (converted instanceof ProductDocument) {
                return (ProductDocument) converted;
            } else if (converted instanceof String) {
                // Se chegou como String, tenta parsear manualmente
                String jsonString = (String) converted;
                log.debug("Converting from String: {}", jsonString);
                return objectMapper.readValue(jsonString, ProductDocument.class);
            } else {
                throw new IllegalArgumentException("Unsupported message type: " + converted.getClass());
            }

        } catch (Exception e) {
            // Fallback: tenta converter diretamente do body
            log.warn("Fallback conversion from raw body");
            return objectMapper.readValue(message.getBody(), ProductDocument.class);
        }
    }

    private void handleProcessingFailure(Message message, Channel channel, long deliveryTag, Exception ex) throws IOException {
        MessageProperties originalProperties = message.getMessageProperties();
        int retryCount = (int) originalProperties.getHeaders()
                .getOrDefault(RabbitMQConstants.RETRY_HEADER, 0);

        log.warn("Retry count: {}/{}", retryCount, properties.getRetry().getMaxAttempts());

        if (retryCount < properties.getRetry().getMaxAttempts()) {
            sendToRetry(message, retryCount);
            log.warn("🔄 Message sent to retry queue, attempt: {}", retryCount + 1);
        } else {
            sendToDeadLetter(message);
            log.error("☠️ Message sent to dead letter queue after max retries");
        }

        channel.basicAck(deliveryTag, false);
    }

    private void sendToRetry(Message originalMessage, int currentRetryCount) {
        MessageProperties retryProperties = createMessageProperties(originalMessage);
        retryProperties.setHeader(RabbitMQConstants.RETRY_HEADER, currentRetryCount + 1);

        Message retryMessage = new Message(originalMessage.getBody(), retryProperties);
        rabbitTemplate.send(properties.getExchanges().getDlx(),
                properties.getRoutingKeys().getRetry5s(),
                retryMessage);
    }

    private void sendToDeadLetter(Message originalMessage) {
        MessageProperties deadLetterProperties = createMessageProperties(originalMessage);
        // Remove o header de retry para a DLQ
        deadLetterProperties.getHeaders().remove(RabbitMQConstants.RETRY_HEADER);

        Message deadLetterMessage = new Message(originalMessage.getBody(), deadLetterProperties);
        rabbitTemplate.send(properties.getExchanges().getDlx(),
                properties.getRoutingKeys().getDead(),
                deadLetterMessage);
    }

    private MessageProperties createMessageProperties(Message originalMessage) {
        MessageProperties props = new MessageProperties();
        props.getHeaders().putAll(originalMessage.getMessageProperties().getHeaders());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(originalMessage.getMessageProperties().getContentEncoding());
        return props;
    }
}
package com.wekers.microsb.service;

import com.fasterxml.jackson.core.JsonParseException;
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
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

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

            if (converted instanceof ProductDocument doc) {
                return doc;
            } else if (converted instanceof String jsonString) {
                log.debug("Converting from String: {}", jsonString);
                return objectMapper.readValue(jsonString, ProductDocument.class);
            } else if (converted instanceof Map<?, ?> map) {
                log.debug("Converting from Map: {}", map);
                // Converte o Map em ProductDocument
                return objectMapper.convertValue(map, ProductDocument.class);
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
        MessageProperties props = message.getMessageProperties();
        int retryCount = (int) props.getHeaders().getOrDefault(RabbitMQConstants.RETRY_HEADER, 0);
        boolean fatal = isFatalError(ex);

        // 🚨 JSON inválido -> DLQ imediata
        if (fatal) {
            log.warn("🚫 Fatal message conversion error → sending directly to DLQ");
            sendToDeadLetter(message);
            channel.basicAck(deliveryTag, false);
            return;
        }

        // ⚙️ Retry controlado
        if (retryCount < properties.getRetry().getMaxAttempts()) {
            sendToRetry(message, retryCount);
            log.warn("🔄 Message sent to retry queue, attempt: {}", retryCount + 1);
        } else {
            sendToDeadLetter(message);
            log.error("☠️ Message sent to dead letter queue after max retries");
        }

        channel.basicAck(deliveryTag, false);
    }

    private boolean isFatalError(Exception ex) {
        Throwable cause = ex.getCause();
        return ex instanceof MessageConversionException
                || ex instanceof JsonParseException
                || (cause != null && cause instanceof JsonParseException)
                || (cause != null && cause instanceof MessageConversionException);
    }

    private void sendToRetry(Message originalMessage, int currentRetryCount) {
        MessageProperties retryProps = createMessageProperties(originalMessage);
        retryProps.setHeader(RabbitMQConstants.RETRY_HEADER, currentRetryCount + 1);
        Message retryMessage = new Message(originalMessage.getBody(), retryProps);
        rabbitTemplate.send(
                properties.getExchanges().getDlx(),
                properties.getRoutingKeys().getRetry5s(),
                retryMessage
        );
    }

    private void sendToDeadLetter(Message originalMessage) {
        MessageProperties deadProps = createMessageProperties(originalMessage);
        deadProps.getHeaders().remove(RabbitMQConstants.RETRY_HEADER);
        Message deadMessage = new Message(originalMessage.getBody(), deadProps);
        rabbitTemplate.send(
                properties.getExchanges().getDlx(),
                properties.getRoutingKeys().getDead(),
                deadMessage
        );
    }

    private MessageProperties createMessageProperties(Message originalMessage) {
        MessageProperties props = new MessageProperties();
        props.getHeaders().putAll(originalMessage.getMessageProperties().getHeaders());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(originalMessage.getMessageProperties().getContentEncoding());
        return props;
    }
}

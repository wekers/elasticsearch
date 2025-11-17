package com.wekers.microsb.service.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.wekers.microsb.config.RabbitMQProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;

@Slf4j
public abstract class ProductBaseHandler {

    protected final RabbitTemplate rabbitTemplate;
    protected final RabbitMQProperties properties;
    protected final ObjectMapper objectMapper;

    protected ProductBaseHandler(RabbitTemplate rabbitTemplate,
                                 RabbitMQProperties properties,
                                 ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    protected MessageProperties copyHeaders(Message original, boolean keepRetryHeader) {
        MessageProperties props = new MessageProperties();
        props.getHeaders().putAll(original.getMessageProperties().getHeaders());
        if (!keepRetryHeader) {
            props.getHeaders().remove("x-retry-count");
        }
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(original.getMessageProperties().getContentEncoding());
        return props;
    }

    protected void sendToRetry(Message msg, int retry) {
        MessageProperties props = copyHeaders(msg, true);
        props.setHeader("x-retry-count", retry + 1);
        rabbitTemplate.send(
                properties.getExchanges().getDlx(),
                properties.getRoutingKeys().getRetry5s(),
                new Message(msg.getBody(), props)
        );
    }

    protected void sendToDlq(Message msg) {
        rabbitTemplate.send(
                properties.getExchanges().getDlx(),
                properties.getRoutingKeys().getDead(),
                new Message(msg.getBody(), copyHeaders(msg, false))
        );
    }

    public void retryOrDlq(Message msg, Channel channel, long tag, Exception e) throws IOException {
        log.error("Error processing message", e);
        int retry = (int) msg.getMessageProperties()
                .getHeaders()
                .getOrDefault("x-retry-count", 0);

        if (retry >= properties.getRetry().getMaxAttempts()) {
            log.error("☠ Max retries reached — sending to DLQ");
            sendToDlq(msg);
            channel.basicAck(tag, false);
        } else {
            log.warn("🔁 Retry attempt {}/{}", retry + 1, properties.getRetry().getMaxAttempts());
            sendToRetry(msg, retry);
            channel.basicAck(tag, false);
        }
    }
}

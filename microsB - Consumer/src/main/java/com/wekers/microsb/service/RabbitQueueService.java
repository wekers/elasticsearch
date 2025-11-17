package com.wekers.microsb.service;

import com.rabbitmq.client.GetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitQueueService {

    private final ConnectionFactory connectionFactory;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Lê mensagens SEM remover da fila.
     * Usa basicGet + basicNack(requeue=true).
     */
    public List<String> peek(String queueName, int limit) {
        List<String> messages = new ArrayList<>();

        try (var connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {

            List<Long> deliveryTags = new ArrayList<>();

            for (int i = 0; i < limit; i++) {
                GetResponse response = channel.basicGet(queueName, false);
                if (response == null) break;

                byte[] body = response.getBody();
                String formatted = MessageUtils.formatMessageBody(body);

                messages.add(formatted);
                deliveryTags.add(response.getEnvelope().getDeliveryTag());
            }

            // requeue todas de uma vez, sem duplicar
            for (Long tag : deliveryTags) {
                channel.basicNack(tag, false, true); // requeue=true
            }

        } catch (Exception e) {
            log.error("Error reading queue: {}", queueName, e);
            messages.add("Erro ao ler fila: " + e.getMessage());
        }

        return messages;
    }



    /**
     * Converte bytes → JSON pretty ou texto simples.
     */
    private String safeFormat(byte[] body) {

        if (body == null) return "<empty>";

        try {
            String raw = new String(body, StandardCharsets.UTF_8);
            return MessageUtils.formatMessageBody(body);
        } catch (Exception ex) {
            log.warn("⚠ Erro ao formatar corpo da mensagem, retornando como texto bruto.", ex);
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /**
     * Remove APENAS a primeira mensagem da fila.
     */
    public boolean deleteFirstMessage(String queueName) {
        try (var connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {

            GetResponse response = channel.basicGet(queueName, false);

            if (response != null) {
                channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                log.info("🗑 Mensagem apagada da fila: {}", queueName);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("❌ Error deleting message from '{}'", queueName, e);
            return false;
        }
    }

    /**
     * Retorna contagem exata (rápido e eficiente).
     */
    public long getMessageCount(String queueName) {

        try (var connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {

            return channel.messageCount(queueName);

        } catch (Exception e) {
            log.error("❌ Error reading queue count '{}'", queueName, e);
            return 0;
        }
    }
}

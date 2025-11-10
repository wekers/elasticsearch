package com.wekers.microsb.service;

import com.rabbitmq.client.GetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitQueueService {

    private final ConnectionFactory connectionFactory;
    private final RabbitTemplate rabbitTemplate;

    public List<String> peek(String queueName, int limit) {
        List<String> messages = new ArrayList<>();

        try (var connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {

            List<byte[]> messageBodies = new ArrayList<>();
            List<Long> deliveryTags = new ArrayList<>();

            for (int i = 0; i < limit; i++) {
                GetResponse response = channel.basicGet(queueName, false);
                if (response == null) break;

                byte[] body = response.getBody();

                // DEBUG DETALHADO
                log.debug("=== DEBUG MESSAGE ===");
                log.debug("Queue: {}", queueName);
                log.debug("Raw bytes: {}", Arrays.toString(body));
                log.debug("Raw string: {}", new String(body));
                log.debug("Hex: {}", bytesToHex(body));
                log.debug("=====================");

                String formattedMessage = MessageUtils.formatMessageBody(body);
                messages.add(formattedMessage);
                messageBodies.add(body);
                deliveryTags.add(response.getEnvelope().getDeliveryTag());
            }

            for (int i = 0; i < messageBodies.size(); i++) {
                channel.basicNack(deliveryTags.get(i), false, true);
            }

        } catch (Exception e) {
            log.error("Error reading queue: {}", queueName, e);
            messages.add("Erro ao ler fila: " + e.getMessage());
        }

        return messages;
    }

    // Método auxiliar para debug em hex
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString();
    }

    public boolean deleteFirstMessage(String queueName) {
        try (var connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {

            GetResponse response = channel.basicGet(queueName, false);
            if (response != null) {
                channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                log.info("Message deleted from queue: {}", queueName);
                return true;
            }
            return false;

        } catch (Exception e) {
            log.error("Error deleting message from queue: {}", queueName, e);
            return false;
        }
    }

    public long getMessageCount(String queueName) {
        try (var connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {
            return channel.messageCount(queueName);
        } catch (Exception e) {
            log.error("Error getting message count for queue: {}", queueName, e);
            return 0;
        }
    }
}
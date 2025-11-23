package com.wekers.microsa.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wekers.microsa.entity.ProductEntity;
import com.wekers.microsa.repository.ProductJpaRepository;
import com.wekers.microsa.service.ProductProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseSeeder {

    private final ProductJpaRepository repository;
    private final ProductProducer producer;
    private final ObjectMapper mapper;
    private final RabbitMQProperties properties;
    private final ConnectionFactory connectionFactory;

    @Bean
    public ApplicationRunner runSeeder() {
        return args -> {

            long count = repository.count();
            if (count > 0) {
                log.info("📦 Database already populated ({} products). Skipping SEED.", count);
                return;
            }

            log.info("🚀 Running DATABASE SEED...");

            // VERIFICAÇÃO DIRETA: Microservice B já criou queues?
            if (!isMicroserviceBQueueAvailable()) {
                log.error("❌ MICROSERVICE B NEVER STARTED - No RabbitMQ structures found");
                log.error("🚫 SEED ABORTED - Start Microservice B first");
                return; // PARA TUDO
            }

            // Carrega arquivo JSON
            InputStream is = getClass().getResourceAsStream("/seed/products-seed.json");
            if (is == null) {
                throw new RuntimeException("seed/products-seed.json NOT FOUND!");
            }

            List<ProductSeedDTO> seedData =
                    mapper.readValue(is, new TypeReference<List<ProductSeedDTO>>() {});

            int eventsSentCount = 0;

            for (ProductSeedDTO dto : seedData) {
                ProductEntity entity = new ProductEntity();
                entity.setName(dto.name());
                entity.setDescription(dto.description());
                entity.setPrice(dto.price());

                // 1) Salva no Postgres (SÓ SE routing key existe)
                ProductEntity saved = repository.save(entity);

                // 2) Dispara evento (SÓ SE routing key existe)
                producer.sendCreated(saved);
                eventsSentCount++;
            }

            log.info("✅ SEED completed with {} products inserted! {} events sent to RabbitMQ.",
                    seedData.size(), eventsSentCount);
        };
    }

    /**
     * VERIFICAÇÃO DIRETA: Checa se a queue específica do Microservice B existe
     */
    private boolean isMicroserviceBQueueAvailable() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);

        try {
            // A queue abaixo foi criada pelo Microservice B?
            String microserviceBQueueName = properties.getRoutingKeys().getCreated() + ".queue";

            Properties queueProps = admin.getQueueProperties(microserviceBQueueName);

            if (queueProps != null) {
                log.info("✅ Microservice B queue '{}' is available", microserviceBQueueName);
                return true;
            } else {
                log.error("❌ Microservice B queue '{}' not found", microserviceBQueueName);
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Cannot connect to RabbitMQ: {}", e.getMessage());
            return false;
        }
    }

    // DTO interno para leitura do JSON
    public record ProductSeedDTO(String name, String description, BigDecimal price) {}
}
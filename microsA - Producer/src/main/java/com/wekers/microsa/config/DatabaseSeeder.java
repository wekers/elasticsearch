package com.wekers.microsa.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wekers.microsa.entity.ProductEntity;
import com.wekers.microsa.repository.ProductJpaRepository;
import com.wekers.microsa.service.ProductProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseSeeder {

    private final ProductJpaRepository repository;
    private final ProductProducer producer;
    private final ObjectMapper mapper;

    @Bean
    public ApplicationRunner runSeeder() {
        return args -> {

            long count = repository.count();
            if (count > 0) {
                log.info("📦 Database already populated ({} products). Skipping SEED.", count);
                return;
            }

            log.info("🚀 Running DATABASE SEED...");

            // Carrega arquivo JSON
            InputStream is = getClass().getResourceAsStream("/seed/products-seed.json");
            if (is == null) {
                throw new RuntimeException("seed/products-seed.json NOT FOUND!");
            }

            List<ProductSeedDTO> seedData =
                    mapper.readValue(is, new TypeReference<List<ProductSeedDTO>>() {});

            for (ProductSeedDTO dto : seedData) {
                ProductEntity entity = new ProductEntity();
                entity.setName(dto.name());
                entity.setDescription(dto.description());
                entity.setPrice(dto.price());

                // 1) Salva no Postgres
                ProductEntity saved = repository.save(entity);

                // 2) Dispara evento → ES via Microserviço B
                producer.sendCreated(saved);
            }

            log.info("✅ SEED completed with {} products inserted!", seedData.size());
        };
    }

    // DTO interno para leitura do JSON
    public record ProductSeedDTO(String name, String description, BigDecimal price) {}
}

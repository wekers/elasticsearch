package com.wekers.microsa.service;

import com.wekers.microsa.config.RabbitQueueAvailable;
import com.wekers.microsa.entity.ProductEntity;
import com.wekers.microsa.repository.ProductJpaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductJpaRepository repository;
    private final ProductProducer producer;
    private final RabbitQueueAvailable queueAvailable;

    public List<ProductEntity> listAll() {
        return repository.findAll();
    }

    @Transactional
    public ProductEntity create(ProductEntity product) {


        // evita duplicidade (name + description)
        repository.findByNameAndDescription(
                product.getName(), product.getDescription()
        ).ifPresent(p -> {
            throw new IllegalArgumentException("Produto já existe (name+description)");
        });

        // Se queue não está disponível, lança exceção (faz rollback)
        if (!queueAvailable.isMicroserviceBQueueAvailable()) {
            throw new IllegalStateException(
                    "Microservice B não está pronto. Fila RabbitMQ não encontrada. " +
                            "Inicie o Microservice B primeiro."
            );
        }

        ProductEntity saved = repository.save(product);
        producer.sendCreated(saved);
        return saved;
    }

    @Transactional
    public ProductEntity update(UUID id, ProductEntity update) {

        ProductEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        entity.setName(update.getName());
        entity.setDescription(update.getDescription());
        entity.setPrice(update.getPrice());

        ProductEntity saved = repository.save(entity);

        // envia evento UPDATED
        producer.sendUpdated(saved);

        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        ProductEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        repository.delete(entity);

        producer.sendDeleted(entity);
    }
}

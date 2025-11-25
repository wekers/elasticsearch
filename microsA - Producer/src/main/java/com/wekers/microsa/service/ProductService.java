package com.wekers.microsa.service;

import com.wekers.microsa.dto.ProductRequest;
import com.wekers.microsa.dto.ProductResponse;
import com.wekers.microsa.entity.ProductEntity;
import com.wekers.microsa.exception.ProductNotFoundException;
import com.wekers.microsa.mapper.ProductMapper;
import com.wekers.microsa.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductJpaRepository repository;
    private final ProductProducer producer;
    private final ProductMapper mapper;

    @Transactional(readOnly = true)
    public List<ProductResponse> listAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        log.info("Criando produto: name='{}', description='{}'", request.name(), request.description());

        // Evita duplicidade (name + description)
        repository.findByNameAndDescription(request.name(), request.description())
                .ifPresent(p -> {
                    throw new IllegalArgumentException("Produto já existe (name+description)");
                });

        ProductEntity entity = mapper.toEntity(request);
        ProductEntity saved = repository.save(entity);

        producer.sendCreated(saved);

        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        ProductEntity entity = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return mapper.toResponse(entity);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        log.info("Atualizando produto id={}", id);

        ProductEntity entity = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        boolean nameChanged = !entity.getName().equals(request.name());
        boolean descriptionChanged = !entity.getDescription().equals(request.description());

        // Só verifica duplicidade se (name + description) realmente mudou
        if (nameChanged || descriptionChanged) {
            repository.findByNameAndDescriptionAndIdNot(
                            request.name(),
                            request.description(),
                            id
                    )
                    .ifPresent(p -> {
                        throw new IllegalArgumentException("Produto já existe (name+description)");
                    });
        }

        mapper.updateEntityFromRequest(request, entity);
        ProductEntity saved = repository.save(entity);

        producer.sendUpdated(saved);

        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        log.info("Removendo produto id={}", id);

        ProductEntity entity = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        repository.delete(entity);

        producer.sendDeleted(entity);
    }
}

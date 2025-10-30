package com.wekers.microsa.service;

import com.wekers.microsa.dto.ProductCreatedEvent;
import com.wekers.microsa.entity.ProductEntity;
import com.wekers.microsa.repository.ProductJpaRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductJpaRepository productJpaRepository;
    private final ProductProducer productProducer;

    public ProductService(ProductJpaRepository productJpaRepository, ProductProducer productProducer) {
        this.productJpaRepository = productJpaRepository;
        this.productProducer = productProducer;
    }

    @Transactional
    public ProductEntity create(ProductEntity product){
        ProductEntity saved = productJpaRepository.save(product);

        ProductCreatedEvent event = new ProductCreatedEvent();
        event.setId(saved.getId());
        event.setName(saved.getName());
        event.setPrice(saved.getPrice());
        event.setDescription(saved.getDescription());

        productProducer.send(saved);

        return saved;
    }

    public List<ProductEntity> listAll(){
        return productJpaRepository.findAll();
    }

}

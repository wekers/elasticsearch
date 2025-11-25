package com.wekers.microsa.mapper;

import com.wekers.microsa.dto.ProductRequest;
import com.wekers.microsa.entity.ProductEntity;
import com.wekers.microsa.dto.ProductResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductEntity toEntity(ProductRequest request) {
        ProductEntity entity = new ProductEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setPrice(request.price());
        return entity;
    }

    public void updateEntityFromRequest(ProductRequest request, ProductEntity entity) {
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setPrice(request.price());
    }

    public ProductResponse toResponse(ProductEntity entity) {
        return new ProductResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice()
        );
    }
}

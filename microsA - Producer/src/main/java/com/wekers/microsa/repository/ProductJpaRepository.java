package com.wekers.microsa.repository;

import com.wekers.microsa.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    // Verificar duplicação (nome + descrição)
    boolean existsByNameAndDescription(String name, String description);

    // Buscar um produto específico pelo nome + descrição
    Optional<ProductEntity> findByNameAndDescription(String name, String description);

    Optional<ProductEntity> findByNameAndDescriptionAndIdNot(String name, String description, UUID id);
}

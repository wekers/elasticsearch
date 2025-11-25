package com.wekers.microsa.controller;


import com.wekers.microsa.dto.ProductRequest;
import com.wekers.microsa.dto.ProductResponse;
import com.wekers.microsa.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;

    // ============================================================
    // CREATE
    // ============================================================
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = service.create(request);
        // 201 + corpo com { "id": ..., "name": ... }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ============================================================
    // READ (by id)
    // ============================================================
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        ProductResponse product = service.getById(id);
        return ResponseEntity.ok(product);
    }

    // ============================================================
    // UPDATE
    // ============================================================
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody ProductRequest request) {
        ProductResponse updated = service.update(id, request);
        return ResponseEntity.ok(updated);
    }

    // ============================================================
    // DELETE
    // ============================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of(
                "message", "Produto removido com sucesso!",
                "id", id
        ));
    }

    // ============================================================
    // LISTAR
    // ============================================================
    @GetMapping
    public ResponseEntity<?> listAll() {
        List<ProductResponse> products = service.listAll();
        if (products.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return ResponseEntity.ok(products);
    }

    // ============================================================
    // HEALTH
    // ============================================================
    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of("status", "UP", "service", "Product Service");
    }
}

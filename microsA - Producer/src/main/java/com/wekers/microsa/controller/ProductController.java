package com.wekers.microsa.controller;

import com.wekers.microsa.entity.ProductEntity;
import com.wekers.microsa.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    // ============================================================
    // CREATE
    // ============================================================
    @PostMapping
    public ResponseEntity<?> create(@RequestBody ProductEntity product) {
        try {
            ProductEntity created = service.create(product);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Produto criado e publicado com sucesso!");
            response.put("product", created);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Erro interno ao criar o produto",
                            "details", e.getMessage()
                    ));
        }
    }

    // ============================================================
    // UPDATE
    // ============================================================
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody ProductEntity product) {
        try {
            ProductEntity updated = service.update(id, product);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Produto atualizado e evento publicado com sucesso!");
            response.put("product", updated);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Erro interno ao atualizar o produto",
                            "details", e.getMessage()
                    ));
        }
    }

    // ============================================================
    // DELETE
    // ============================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        try {
            service.delete(id);
            return ResponseEntity.ok(Map.of(
                    "message", "Produto removido com sucesso!",
                    "id", id
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Erro interno ao remover o produto",
                            "details", e.getMessage()
                    ));
        }
    }

    // ============================================================
    // LISTAR
    // ============================================================
    @GetMapping
    public ResponseEntity<?> listAll() {
        List<ProductEntity> products = service.listAll();
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

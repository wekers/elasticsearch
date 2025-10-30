package com.wekers.microsb.controller;

import com.wekers.microsb.document.ProductDocument;
import com.wekers.microsb.repository.ProductEsRepository;
import com.wekers.microsb.service.ProductSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search/products")
public class ProductSearchController {

    private final ProductSearchService service;

    public ProductSearchController(ProductSearchService service, ProductEsRepository esRepository) {
        this.service = service;
    }

    @GetMapping("/{keyword}")
    public List<ProductDocument> search(@PathVariable String keyword) {
        return service.search(keyword);
    }


    // retorna todos indexados no Elasticsearch
    @GetMapping("/listar-todos")
    public List<ProductDocument> listarTodos() {
        return service.findAll();
    }
}

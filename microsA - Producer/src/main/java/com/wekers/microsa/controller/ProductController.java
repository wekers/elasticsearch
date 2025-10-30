package com.wekers.microsa.controller;

import com.wekers.microsa.entity.ProductEntity;
import com.wekers.microsa.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    public ProductEntity create(@RequestBody ProductEntity product){
        return service.create(product);
    }

    @GetMapping
    public List<ProductEntity> listAll(){
        return service.listAll();
    }

}

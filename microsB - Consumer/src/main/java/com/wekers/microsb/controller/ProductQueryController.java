package com.wekers.microsb.controller;

import com.wekers.microsb.document.ProductDocument;
import com.wekers.microsb.service.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/catalogo")
@RequiredArgsConstructor
public class ProductQueryController {

    private final ProductQueryService service;

    @GetMapping("/products/{id}")
    public ProductDocument getById(@PathVariable String id) {
        return service.findById(id);
    }
}

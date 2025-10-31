package com.wekers.microsb.controller;

import com.wekers.microsb.document.ProductDocument;
import com.wekers.microsb.service.CatalogSearchService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/catalogo")
public class CatalogController {

    private final CatalogSearchService service;

    public CatalogController(CatalogSearchService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public Page<ProductDocument> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "price,asc") String sort
    ) {
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        String sortDirection = sortParts.length > 1 ? sortParts[1] : "asc";

        return service.search(query, minPrice, maxPrice, page, size, sortField, sortDirection);
    }

}

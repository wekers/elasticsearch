package com.wekers.microsb.controller;

import com.wekers.microsb.dto.CatalogSearchResponse;
import com.wekers.microsb.service.CatalogSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalogo")
public class CatalogController {

    private final CatalogSearchService service;

    public CatalogController(CatalogSearchService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public CatalogSearchResponse search(
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

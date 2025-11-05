package com.wekers.microsb.dto;

import com.wekers.microsb.document.ProductDocument;
import org.springframework.data.domain.Page;

import java.util.List;

public record CatalogSearchResponse(
        Page<ProductDocument> results,
        List<String> suggestions,
        String correctedQuery
) {}

package com.wekers.microsb.service;

import co.elastic.clients.json.JsonData;
import com.wekers.microsb.document.ProductDocument;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CatalogSearchService {

    private final ElasticsearchOperations operations;

    public CatalogSearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public Page<ProductDocument> search(
            String query,
            Double minPrice,
            Double maxPrice,
            int page,
            int size,
            String sortField,
            String sortDirection
    ) {

        List<Query> must = new ArrayList<>();

        // Full-text fuzzy + boost
        if (query != null && !query.isBlank()) {
            must.add(Query.of(q -> q.multiMatch(
                    MultiMatchQuery.of(m -> m
                            .query(query)
                            .fields("name^3", "description^1")
                            .fuzziness("AUTO")
                    )
            )));
        }

        // Price range - Filtro por preço mínimo / máximo
        if (minPrice != null || maxPrice != null) {
            must.add(Query.of(q -> q.range(
                    RangeQuery.of(r -> r
                            .field("price")
                            .gte(minPrice != null ? JsonData.of(minPrice) : null)
                            .lte(maxPrice != null ? JsonData.of(maxPrice) : null)
                    )
            )));
        }

        BoolQuery boolQuery = BoolQuery.of(b -> b.must(must));

        // HIGHLIGHT
        HighlightParameters highlightParameters = HighlightParameters.builder()
                .withPreTags("<strong>")
                .withPostTags("</strong>")
                .build();

        Highlight highlight = new Highlight(
                highlightParameters,
                List.of(
                        new HighlightField("name"),
                        new HighlightField("description")
                )
        );


        var nativeQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(boolQuery)))
                .withPageable(PageRequest.of(page, size))
                .withSort(s -> s.field(f -> f
                        .field(sortField)
                        .order(sortDirection.equalsIgnoreCase("desc") ? SortOrder.Desc : SortOrder.Asc)))
                .withHighlightQuery(new HighlightQuery(highlight, ProductDocument.class))
                .build();

        var searchHits = operations.search(nativeQuery, ProductDocument.class);

        List<ProductDocument> results = searchHits.getSearchHits().stream().map(hit -> {
            var p = hit.getContent();
            hit.getHighlightFields().forEach((field, values) -> {
                switch (field) {
                    case "name" -> p.setName(values.get(0));
                    case "description" -> p.setDescription(values.get(0));
                }
            });
            return p;
        }).toList();

        return new PageImpl<>(results, PageRequest.of(page, size), searchHits.getTotalHits());
    }
}

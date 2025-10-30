package com.wekers.microsb.service;

import com.wekers.microsb.document.ProductDocument;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductSearchService {

    private final ElasticsearchOperations operations;

    public ProductSearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public List<ProductDocument> search(String keyword) {

        Query esQuery = Query.of(q -> q.multiMatch(
                MultiMatchQuery.of(m -> m
                        .query(keyword)
                        .fields("name", "description")
                        .fuzziness("AUTO")
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                )
        ));

        var query = NativeQuery.builder()
                .withQuery(esQuery)
                .build();

        return operations.search(query, ProductDocument.class)
                .getSearchHits()
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }

    public List<ProductDocument> findAll() {
        return operations.search(
                        NativeQuery.builder().build(),
                        ProductDocument.class
                )
                .getSearchHits()
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }

}

package com.wekers.microsb.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.wekers.microsb.document.ProductDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutocompleteService {

    private final ElasticsearchOperations operations;

    public AutocompleteService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public List<String> suggest(String prefix) {

        var query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.prefix(p -> p
                        .field("name")
                        .value(prefix.toLowerCase())
                )))
                .withPageable(PageRequest.of(0, 10)) // máximo 10 sugestões
                .build();

        return operations.search(query, ProductDocument.class)
                .getSearchHits()
                .stream()
                .map(hit -> hit.getContent().getName())
                .distinct()
                .toList();
    }
}
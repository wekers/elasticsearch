package com.wekers.microsb.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.wekers.microsb.document.ProductDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutocompleteService {

    private final ElasticsearchOperations operations;

    public AutocompleteService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public List<String> suggest(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return List.of();
        }

        String cleanPrefix = prefix.trim().toLowerCase();

        // Query melhorada para autocomplete
        var query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.matchPhrasePrefix(m -> m
                        .field("name")
                        .query(cleanPrefix)
                        .maxExpansions(10)
                )))
                .withPageable(PageRequest.of(0, 10))
                .build();

        try {
            SearchHits<ProductDocument> searchHits = operations.search(query, ProductDocument.class);

            return searchHits.getSearchHits()
                    .stream()
                    .map(hit -> hit.getContent().getName())
                    .distinct()
                    .limit(10)
                    .toList();

        } catch (Exception e) {
            // Log do erro para debug
            System.err.println("Erro no autocomplete: " + e.getMessage());
            return List.of();
        }
    }

    // Método alternativo usando wildcard (mais tolerante)
    public List<String> suggestWildcard(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return List.of();
        }

        String cleanPrefix = "*" + prefix.trim().toLowerCase() + "*";

        var query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.wildcard(w -> w
                        .field("name")
                        .value(cleanPrefix)
                        .caseInsensitive(true)
                )))
                .withPageable(PageRequest.of(0, 10))
                .build();

        try {
            SearchHits<ProductDocument> searchHits = operations.search(query, ProductDocument.class);

            return searchHits.getSearchHits()
                    .stream()
                    .map(hit -> hit.getContent().getName())
                    .distinct()
                    .limit(10)
                    .toList();

        } catch (Exception e) {
            System.err.println("Erro no wildcard suggest: " + e.getMessage());
            return List.of();
        }
    }
}
package com.wekers.microsb.service;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.wekers.microsb.document.ProductDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AutocompleteService {

    private final ElasticsearchOperations operations;

    public AutocompleteService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    // ============================================================
    //       AUTOCOMPLETE PRO (Multi-strategy + fallback)
    // ============================================================
    public List<String> suggest(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return List.of();
        }

        String clean = prefix.trim().toLowerCase();

        // 1) Query principal (Phrase Prefix + Multi Match)
        List<String> primary = searchPhrasePrefix(clean);
        if (!primary.isEmpty()) return primary;

        // 2) Query secundária (Wildcard: *term*)
        List<String> wildcard = searchWildcard(clean);
        if (!wildcard.isEmpty()) return wildcard;

        // 3) Query terciária (Fuzzy: tolera erros)
        return searchFuzzy(clean);
    }

    // ============================================================
    // 1) Phrase Prefix (boosta hits mais rápidos e relevantes)
    // ============================================================
    private List<String> searchPhrasePrefix(String clean) {
        var query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.multiMatch(mm -> mm
                        .fields("name", "nameSpell")
                        .query(clean)
                        .type(TextQueryType.PhrasePrefix)
                        .maxExpansions(20)
                )))
                .withPageable(PageRequest.of(0, 10))
                .build();

        return extractResults(query);
    }

    // ============================================================
    // 2) Wildcard (*term*) — mais tolerante
    // ============================================================
    private List<String> searchWildcard(String clean) {
        var wildcardQuery = "*" + clean + "*";

        var query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(b -> b
                        .should(s -> s.wildcard(w -> w
                                .field("name")
                                .value(wildcardQuery)
                                .caseInsensitive(true)
                        ))
                        .should(s -> s.wildcard(w -> w
                                .field("nameSpell")
                                .value(wildcardQuery)
                                .caseInsensitive(true)
                        ))
                )))
                .withPageable(PageRequest.of(0, 10))
                .build();

        return extractResults(query);
    }

    // ============================================================
    // 3) Fuzzy (corrige erros de digitação)
    // ============================================================
    private List<String> searchFuzzy(String clean) {
        var query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.match(m -> m
                        .field("name")
                        .query(clean)
                        .fuzziness("AUTO")      // tolera 1 erro
                        .maxExpansions(20)
                )))
                .withPageable(PageRequest.of(0, 10))
                .build();

        return extractResults(query);
    }

    // ============================================================
    // Extração + remoção de duplicados (name + description)
    // ============================================================
    private List<String> extractResults(NativeQuery query) {
        try {
            SearchHits<ProductDocument> hits = operations.search(query, ProductDocument.class);

            return hits.getSearchHits().stream()
                    .map(hit -> hit.getContent().getName())
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(10)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Erro no autocomplete: " + e.getMessage());
            return List.of();
        }
    }
}

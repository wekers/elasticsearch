package com.wekers.microsb.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wekers.microsb.document.ProductDocument;
import com.wekers.microsb.dto.CatalogSearchResponse;
import org.elasticsearch.client.Request;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CatalogSearchService {

    private final ElasticsearchOperations operations;
    private final ElasticsearchClient esClient;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SEARCH_INDEX = "products_read";

    public CatalogSearchService(ElasticsearchOperations operations, ElasticsearchClient esClient) {
        this.operations = operations;
        this.esClient = esClient;
    }

    public CatalogSearchResponse search(
            String query,
            Double minPrice,
            Double maxPrice,
            int page,
            int size,
            String sortField,
            String sortDirection
    ) {

        List<Query> must = new ArrayList<>();
        final String originalQuery = query;
        String correctedQuery = null;
        List<String> suggestions = new ArrayList<>();

        // SEMPRE verificar correção ortográfica
        if (query != null && !query.isBlank()) {
            correctedQuery = correctQuery(query);
            suggestions = getSpellSuggestionsList(query);

            // ✅ CORREÇÃO: SEMPRE usa a correção se disponível
            if (!suggestions.isEmpty() && !suggestions.get(0).equalsIgnoreCase(query)) {
                correctedQuery = suggestions.get(0);
                query = correctedQuery; // ✅ USA A CORREÇÃO NA BUSCA
                log.info("🎯 Correção aplicada na busca: '{}' -> '{}'", originalQuery, correctedQuery);
            } else {
                correctedQuery = originalQuery; // Mantém original se não há correção
            }

            final String finalQuery = query;
            must.add(Query.of(q -> q.multiMatch(
                    MultiMatchQuery.of(m -> m
                            .query(finalQuery)
                            .fields("name.standard^3", "description^1")
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

        var searchHits = operations.search(nativeQuery, ProductDocument.class, IndexCoordinates.of(SEARCH_INDEX));

        // ✅ MELHORIA: Lógica aprimorada para sugestões
        boolean hasNoResults = searchHits.getTotalHits() == 0;
        boolean hasFewResults = searchHits.getTotalHits() <= 3;

        List<String> finalSuggestions = suggestions;

        // ✅ Se não tem sugestões de spell check E tem poucos/nenhum resultado, busca sugestões de vocabulário
        if ((hasNoResults || hasFewResults) && finalSuggestions.isEmpty()) {
            finalSuggestions = suggestVocabulary();

            // ✅ Fallback se ainda não tem sugestões
            if (finalSuggestions.isEmpty()) {
                finalSuggestions = getFallbackSuggestions();
            }
        }

        // Se não encontrou resultados, retorna com sugestões
        if (hasNoResults) {
            return new CatalogSearchResponse(
                    new PageImpl<>(List.of(), PageRequest.of(page, size), 0),
                    finalSuggestions,
                    correctedQuery
            );
        }

        // Encontrou resultados - retorna com sugestões e correção se aplicável
        Page<ProductDocument> results = buildHighlightedResult(searchHits, page, size);

        // ✅ CORREÇÃO: Marca correção mesmo quando há resultados
        if (correctedQuery != null && !correctedQuery.equalsIgnoreCase(originalQuery)) {
            final String finalCorrectedQuery = correctedQuery;
            results.getContent().forEach(item -> item.setCorrectedQuery(finalCorrectedQuery));

            log.info("🎯 Correção aplicada nos resultados: '{}' -> '{}' ({} resultados)",
                    originalQuery, correctedQuery, searchHits.getTotalHits());
        }

        return new CatalogSearchResponse(
                results,
                finalSuggestions,
                correctedQuery // ✅ Agora retorna a correção real
        );
    }

    private List<String> getSpellSuggestionsList(String query) {
        if (query == null || query.isBlank()) return List.of();

        try {
            var transport = (co.elastic.clients.transport.rest_client.RestClientTransport) esClient._transport();
            var low = transport.restClient();

            String json = """
        {
          "suggest": {
            "text": "%s",
            "spell_suggestion": {
              "term": {
                "field": "nameSpellClean",
                "size": 3,
                "suggest_mode": "popular",
                "max_edits": 2,
                "prefix_length": 1,
                "min_doc_freq": 1
              }
            }
          }
        }
        """.formatted(query.replace("\"", "\\\""));

            var request = new Request("POST", "/" + SEARCH_INDEX + "/_search");
            request.setJsonEntity(json);

            var response = low.performRequest(request);
            var root = mapper.readTree(response.getEntity().getContent());

            var suggest = root.path("suggest").path("spell_suggestion");
            if (suggest.isArray() && suggest.size() > 0) {
                var firstSuggestion = suggest.get(0);
                var options = firstSuggestion.path("options");

                if (options.isArray() && options.size() > 0) {
                    List<String> suggestions = new ArrayList<>();
                    for (var option : options) {
                        String suggestion = option.path("text").asText();
                        if (!suggestion.isBlank() && !suggestion.equalsIgnoreCase(query)) {
                            suggestions.add(suggestion);
                        }
                    }
                    log.debug("🔍 Spell suggestions para '{}': {}", query, suggestions);
                    return suggestions;
                }
            }

            log.debug("🔍 Nenhuma spell suggestion para '{}'", query);
            return List.of();

        } catch (Exception e) {
            log.error("Error getting spell suggestions for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private String correctQuery(String originalQuery) {
        if (originalQuery == null || originalQuery.isBlank()) return originalQuery;

        List<String> suggestions = getSpellSuggestionsList(originalQuery);

        // Retorna a primeira sugestão se existir, senão retorna o original
        return suggestions.isEmpty() ? originalQuery : suggestions.get(0);
    }

    private List<String> suggestVocabulary() {
        try {
            var query = NativeQuery.builder()
                    .withQuery(q -> q.matchAll(m -> m))
                    .withPageable(PageRequest.of(0, 100))
                    .build();

            var hits = operations.search(query, ProductDocument.class);

            return hits.getSearchHits()
                    .stream()
                    .map(hit -> hit.getContent().getName())
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(this::extractMainProductTerm) // ✅ Extrai termos principais
                    .filter(term -> term != null && term.length() >= 3) // ✅ Filtra termos muito curtos
                    .distinct()
                    .sorted()
                    .limit(15) // ✅ Mais sugestões
                    .toList();

        } catch (Exception e) {
            log.error("Erro no suggestVocabulary: {}", e.getMessage());
            return getFallbackSuggestions(); // ✅ Usa fallback em caso de erro
        }
    }

    // ✅ MÉTODO: Extrai termos principais dos nomes dos produtos
    private String extractMainProductTerm(String productName) {
        if (productName == null) return null;

        // Remove códigos hexadecimais
        String cleanName = productName.replaceAll("[a-f0-9]{6}$", "").trim();

        // Extrai a primeira palavra significativa
        String[] words = cleanName.split("\\s+");
        if (words.length > 0) {
            String firstWord = words[0];
            // Retorna apenas se for uma palavra significativa (não contém números)
            if (firstWord.length() >= 3 && !firstWord.matches(".*\\d.*")) {
                return firstWord.toLowerCase();
            }
        }

        // Fallback: retorna o nome limpo em lowercase
        return cleanName.toLowerCase();
    }

    // ✅ MÉTODO: Sugestões de fallback
    private List<String> getFallbackSuggestions() {
        return List.of("headset", "notebook", "monitor", "teclado", "mouse",
                "ssd", "processador", "placa de vídeo", "memória", "fonte",
                "gabinete", "water cooler", "ventoinha", "hub", "cabo");
    }

    private Page<ProductDocument> buildHighlightedResult(
            SearchHits<ProductDocument> searchHits, int page, int size) {

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
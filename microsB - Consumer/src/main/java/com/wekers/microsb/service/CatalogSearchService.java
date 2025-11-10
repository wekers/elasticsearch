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
    private final ElasticsearchClient esClient;

    private final ObjectMapper mapper = new ObjectMapper();

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

        // Full-text fuzzy + boost
        if (query != null && !query.isBlank()) {
            must.add(Query.of(q -> q.multiMatch(
                    MultiMatchQuery.of(m -> m
                            .query(query)
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

        HighlightQuery highlightQuery = new HighlightQuery(highlight, ProductDocument.class);


        var nativeQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(boolQuery)))
                .withPageable(PageRequest.of(page, size))
                .withSort(s -> s.field(f -> f
                        .field(sortField)
                        .order(sortDirection.equalsIgnoreCase("desc") ? SortOrder.Desc : SortOrder.Asc)))
                .withHighlightQuery(new HighlightQuery(highlight, ProductDocument.class))
                .build();

        var searchHits = operations.search(nativeQuery, ProductDocument.class);



        // Se encontrou, beleza
        if (searchHits.getTotalHits() > 0) {
            return new CatalogSearchResponse(
                    buildHighlightedResult(searchHits, page, size),
                    List.of(),
                    null
            );

        }

        if (searchHits.getTotalHits() == 0) {
            return new CatalogSearchResponse(
                    new PageImpl<>(List.of(), PageRequest.of(page, size), 0),
                    suggestVocabulary(),
                    null
            );
        }


        // Caso contrário → tentar correção
        String corrected = correctQuery(query);

        // Se a correção não mudou nada → retorna sugestões
        if (corrected.equalsIgnoreCase(query)) {
            return new CatalogSearchResponse(
                    new PageImpl<>(List.of(), PageRequest.of(page, size), 0),
                    suggestVocabulary(), // sugerimos termos úteis
                    null // não houve correção
            );
        }

        // Reexecuta a busca usando o termo corrigido
        var correctedQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.multiMatch(
                        MultiMatchQuery.of(m -> m
                                .query(corrected)
                                .fields("name^3", "description^1")
                                .fuzziness("AUTO")
                        )
                )))
                .withHighlightQuery(highlightQuery) // reutilize o mesmo highlight!
                .withPageable(PageRequest.of(page, size))
                .build();

        var correctedHits = operations.search(correctedQuery, ProductDocument.class);

        // Retorna resultados + informação da correção
        var resultPage = buildHighlightedResult(correctedHits, page, size);
        resultPage.getContent().forEach(item -> item.setCorrectedQuery(corrected));
        return new CatalogSearchResponse(
                resultPage,
                List.of(corrected),
                corrected
        );


    }

    private List<String> suggestVocabulary() {
        try {
            var query = NativeQuery.builder()
                    .withQuery(Query.of(q -> q.exists(e -> e.field("name"))))
                    .withPageable(PageRequest.of(0, 50)) // ✅ Aumentei para 50
                    .withFields("name") // ✅ Busca apenas o campo name
                    .build();

            var hits = operations.search(query, ProductDocument.class);

            return hits.getSearchHits()
                    .stream()
                    .map(hit -> hit.getContent().getName())
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .distinct()
                    .sorted()
                    .limit(20) // Limita para 20 sugestões
                    .toList();

        } catch (Exception e) {
            System.err.println("Erro no suggestVocabulary: " + e.getMessage());
            return List.of();
        }
    }

    private String correctQuery(String originalQuery) {
        if (originalQuery == null || originalQuery.isBlank()) return originalQuery;

        try {
            var transport = (co.elastic.clients.transport.rest_client.RestClientTransport) esClient._transport();
            var low = transport.restClient();

            // JSON corrigido para spellcheck
            String json = """
        {
          "suggest": {
            "text": "%s",
            "simple_phrase": {
              "phrase": {
                "field": "name_spell",
                "size": 1,
                "gram_size": 2,
                "direct_generator": [{
                  "field": "name_spell",
                  "suggest_mode": "popular"
                }],
                "highlight": {
                  "pre_tag": "<em>",
                  "post_tag": "</em>"
                }
              }
            }
          }
        }
        """.formatted(originalQuery.replace("\"", "\\\""));

            var request = new Request("POST", "/products/_search");
            request.setJsonEntity(json);

            var response = low.performRequest(request);
            var root = mapper.readTree(response.getEntity().getContent());

            var suggest = root.path("suggest").path("simple_phrase");
            if (suggest.isArray() && suggest.size() > 0) {
                var options = suggest.get(0).path("options");
                if (options.isArray() && options.size() > 0) {
                    var suggestion = options.get(0).path("text").asText();
                    if (!suggestion.isBlank() && !suggestion.equalsIgnoreCase(originalQuery)) {
                        return suggestion;
                    }
                }
            }

            return originalQuery;

        } catch (Exception e) {
            System.err.println("Erro no correctQuery: " + e.getMessage());
            return originalQuery;
        }
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

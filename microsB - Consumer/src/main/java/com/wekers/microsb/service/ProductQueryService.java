package com.wekers.microsb.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wekers.microsb.document.ProductDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private final ElasticsearchClient esClient;

    private static final String INDEX = "products_read"; // alias padrão

    public ProductDocument findById(String id) {
        try {
            var response = esClient.get(g -> g
                            .index(INDEX)
                            .id(id),
                    ProductDocument.class
            );

            if (!response.found()) {
                throw new RuntimeException("Produto não encontrado: " + id);
            }

            return response.source();

        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar produto: " + id + " → " + e.getMessage(), e);
        }
    }
}

package com.wekers.microsb.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

@Component
public class ProductIndexManager {

    private final ElasticsearchClient es;

    private static final String BASE = "products";
    private static final String READ_ALIAS = "products_read";
    private static final String WRITE_ALIAS = "products_write";

    public ProductIndexManager(ElasticsearchClient es) {
        this.es = es;
    }

    @PostConstruct
    public void setup() {
        try {
            // Se já existe alias READ → já temos versão ativa → nada a fazer
            if (es.indices().exists(b -> b.index(READ_ALIAS)).value()) {
                System.out.println("✅ Alias já existe. Índice pronto.");
                return;
            }

            // Criar índice inicial v1
            String versionedIndex = BASE + "_v1";

            System.out.println("⚠️ Criando índice inicial: " + versionedIndex);

            InputStream is = getClass().getResourceAsStream("/elasticsearch/product-settings.json");
            if (is == null) throw new RuntimeException("Arquivo product-settings.json não encontrado!");

            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            es.indices().create(c -> c
                    .index(versionedIndex)
                    .withJson(new StringReader(json))
            );

            // Criar aliases
            es.indices().putAlias(a -> a.index(versionedIndex).name(READ_ALIAS));
            es.indices().putAlias(a -> a.index(versionedIndex).name(WRITE_ALIAS));

            System.out.println("✅ Versão ativa → " + versionedIndex);
            System.out.println("✅ Alias READ → " + READ_ALIAS);
            System.out.println("✅ Alias WRITE → " + WRITE_ALIAS);

        } catch (Exception e) {
            System.err.println("❌ Erro ao configurar índice: " + e.getMessage());
        }
    }
}

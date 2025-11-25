package com.wekers.microsb.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;


@Slf4j
@Configuration
@RequiredArgsConstructor
public class ElasticsearchHealthCheck implements InitializingBean {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("🏥 Verificando saúde do Elasticsearch...");

        try {
            HealthResponse health = elasticsearchClient.cluster().health();
            String clusterStatus = health.status() != null ? health.status().name() : "UNKNOWN";
            log.info("📊 Status do cluster: {}", clusterStatus);

            IndexOperations indexOps = elasticsearchOperations.indexOps(com.wekers.microsb.document.ProductDocument.class);
            boolean indexExists = indexOps.exists();

            if (!indexExists) {
                System.err.println("\n" +
                        "❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌\n" +
                        "❌ ÍNDICE ELASTICSEARCH NÃO ENCONTRADO!\n" +
                        "❌ Por Favor, crie o índice\n" +
                        "❌ \n" +
                        "❌     Execute o comando:\n" +
                        "❌        sh scripts/reset-index.sh\n" +
                        "❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌\n");

                // Dá um tempo para o usuário ler a mensagem antes de fechar
                Thread.sleep(2000);
                System.exit(1);
            }

            log.info("✅ Índice 'products' verificado com sucesso");

        } catch (Exception e) {
            System.err.println("\n❌ Erro ao conectar com Elasticsearch: " + e.getMessage());
            Thread.sleep(2000);
            System.exit(1);
        }
    }

    @PreDestroy
    public void onDestroy() {
        log.info("🔄 Finalizando conexão com Elasticsearch...");
    }
}
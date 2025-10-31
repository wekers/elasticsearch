🔄 Arquitetura Assíncrona

    Microserviço A (Producer)

        Usa JPA para salvar os dados no banco relacional (Postgres/H2).

        Publica um evento (RabbitMQ) dizendo que um novo produto foi criado/atualizado.

    Microserviço B (Consumer)

        Escuta esses eventos.

        Indexa/atualiza o documento no Elasticsearch.

✅ Assim:

    Banco relacional → fonte de verdade (source of truth).

    Elasticsearch → apenas um motor de busca sincronizado por eventos.

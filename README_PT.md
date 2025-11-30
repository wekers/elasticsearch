<p align="center">
  <img src="./banner.png" alt="Wekers Distributed Catalog Banner" width="100%">
</p>

# 📘 Wekers - Elasticsearch Microsserviço A/B

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)
![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.x-orange)
![License](https://img.shields.io/badge/License-MIT-yellow)

### **Catálogo de Produtos com PostgreSQL + RabbitMQ + Elasticsearch**

### **Microserviços A (Producer) + B (Consumer/Query)**

---
## 🌐 Language
- 🇧🇷 Você está lendo a versão em Português.
- 🇺🇸 [English version](https://github.com/wekers/elasticsearch)
---

## 📑 Sumário
- [🎯 Visão Geral](#-visão-geral)
- [🏗 Arquitetura](#-arquitetura)
- [🛠 Stack Tecnológica](#-stack-tecnológica)
- [🚀 Primeira Execução](#-primeira-execução)
- [📡 API Endpoints](#-api-endpoints)
- [🔍 Funcionalidades Avançadas](#-funcionalidades-avançadas)
- [🐇 Mensageria RabbitMQ](#-mensageria-rabbitmq)
- [📊 Dashboard de Filas](#-dashboard-de-filas)
- [🗂 Gestão Elasticsearch](#-gestão-elasticsearch)
- [💾 Backup & Restore](#-backup--restore)
- [📚 Como Estudar](#-como-estudar-este-projeto)
- [🐛 Troubleshooting](#-troubleshooting-comum)
- [🚀 Como Evoluir](#-como-evoluir-este-projeto)
---
## 🚀 Pré-requisitos
- Docker & Docker Compose
- Java 21
- Maven
- Git
- jq (para scripts JSON)

---

## 🎯 Visão Geral
Este projeto demonstra um **Catálogo Distribuído** de busca avançada, autocomplete, correção ortográfica, indexação assíncrona, DLQ, Retry, seed inteligente, dashboard de filas e Scripts de Manutenção DevOps, dividido em:


#### **Microsserviço A (Producer)**

- CRUD do PostgreSQL
- Envio de eventos para o RabbitMQ
- Seed automático de 500 produtos

#### **Microsserviço B (Consumer)**

- Consome mensagens do RabbitMQ
- Indexa produtos no Elasticsearch
- Fornece APIs de:
    - /search/products
    - /catalogo/search
    - /catalogo/suggest
    - Autocomplete PRO (edge-ngram + wildcard)
    - Correção ortográfica (spellcheck)
    - Filtro por preço, ordenação e paginação

---

## 🏗 Arquitetura

### Fluxo Alto Nível

1. Usuário chama **Microserviço A** (`/products`) para *criar/atualizar/deletar*.
2. **Microserviço A** grava no **PostgreSQL** (estado de verdade).
3. **Microserviço A** publica evento no **RabbitMQ** (`products.exchange`).
4. O **Microserviço B** consome os eventos, aplica regras de idempotência, e indexa no **Elasticsearch**.
5. O cliente consome buscas e autocomplete via **Microserviço B**.

```text
┌─────────┐    HTTP/JSON    ┌──────────────┐    JDBC     ┌─────────────┐
│ Usuário │ ──────────────► │ Microserviço │ ──────────► │ PostgreSQL  │
│         │                 │      A       │             │             │
│         │                 └──────────────┘             └─────────────┘
│         │                         │
│         │                         │ RabbitMQ
│         │                         ▼
│         │                 ┌──────────────┐    REST     ┌─────────────┐
│ Cliente │ ──────────────► │ Microserviço │ ──────────► │ Elastic-    │
│         │   HTTP/JSON     │      B       │             │   search    │
└─────────┘                 └──────────────┘             └─────────────┘
```

### C4 – Level 1 (System Context)

![C4 Level 1](docs/diagrams/c4-level-1-system-context.svg)

---

### C4 – Level 2 (Containers)

![C4 Level 2](docs/diagrams/c4-level-2-containers.svg)

---

### 🔁 Fluxo Completo (Sequence Diagram)

![Fluxo Completo](docs/diagrams/full-sequence-flow.svg)

* * *

🛠 Stack Tecnológica
--------------------

*   **Linguagem**: Java 21

*   **Framework**: Spring Boot 3.x

*   **Banco transacional**: PostgreSQL 16 (em Docker)

*   **Busca**: Elasticsearch 8.x (em Docker)

*   **Mensageria**: RabbitMQ 3-management (em Docker)

*   **Migrações DB**: Flyway

*   **Build**: Maven

*   **Dashboard de filas**: Thymeleaf + RabbitMQ Management UI

*   **Scripts utilitários** (bash + `jq` + `curl`)


* * *

🚀 Primeira Execução
----------------------------------

### ⚠️ **Ordem Correta é CRUCIAL**
```bash
# 1. Clone e prepare
git clone git@github.com:wekers/elasticsearch.git
cd elasticsearch/
    
# 2. Subir infraestrutura
docker compose up -d
```
PrintScreen:</br>
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/docker-compose_1.png)
```bash
# 3. Resetar índice Elasticsearch
cd "microsB - Consumer"
sh scripts/reset-index.sh
```
PrintScreen:</br>
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/script_reset-index.png)
```bash
# 4. Iniciar Microserviço B (terminal 1)
./mvnw spring-boot:run
    
# 5. Iniciar Microserviço A (terminal 2)
cd "../microsA - Producer"
./mvnw spring-boot:run
```
### 📥 Arquivos de Teste

Baixe os arquivos na raiz do projeto para testar:

*   [`postman_collection.json`](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/Wekers-Elasticsearch-uServ-A-B.postman_collection.json)

*   [`api.http`](https://github.com/wekers/elasticsearch/blob/main/api.http)


* * *

📡 API Endpoints
----------------

| Método | Endpoint | Serviço | Descrição |
| --- | --- | --- | --- |
| **POST** | `/products` | A | Criar produto |
| **PUT** | `/products/{id}` | A | Atualizar produto |
| **DELETE** | `/products/{id}` | A | Deletar produto |
| **GET** | `/catalogo/search` | B | Busca avançada + fuzzy |
| **GET** | `/catalogo/suggest` | B | Autocomplete |
| **GET** | `/catalogo/products/{id}` | B | Busca por ID |
| **GET** | `/queues` | B | Dashboard filas |

### 🔌 Portas dos serviços
```bash
- 8080 → Microserviço A
- 8081 → Microserviço B
- 5435 → PostgreSQL
- 5672 / 15672 → RabbitMQ
- 9200 → Elasticsearch
- 5601 → Kibana
```

### 📲 Criar produto:
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/api_http_1.png)

### 📲 Buscar produto (com erro de digitação):
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/api_http_2.png)

### ✨ Fluxo da busca

![Fluxo da busca](docs/diagrams/search-flow.svg)


### 📲 Postman Exemplo com erro de digitação:
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/postman_1.png)

* * *

🔍 Funcionalidades Avançadas
----------------------------

### 🅰 Microserviço A – Producer (PostgreSQL + Eventos)

**Responsabilidades:**

*   CRUD de `ProductEntity` via `/products`

*   Persistência no **PostgreSQL**

*   Emissão de eventos para RabbitMQ

*   Seed automático de 500 produtos


**🌱 Seed Inteligente:**

*   Executa apenas se tabela vazia

*   Verifica saúde do RabbitMQ antes

*   Baseado em `src/main/resources/seed/products-seed.json`


**Fluxo do Seed:**

![Fluxo Seed](docs/diagrams/seed-flow.svg)


> ⚠️ **Importante**: O seeder **não roda** se o broker não estiver OK
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/microsa_start_fail_1.png)
---
**PostgreSQL com DBeaver:**  
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/Postgresql_DBeaver.png)

### 🅱 Microserviço B – Consumer/Search (Elasticsearch)

**Responsabilidades:**

- Consumir eventos do RabbitMQ:
- Sincronizar índice de catálogo no Elasticsearch.
- Expor APIs de busca:
    - `/catalogo/products/{id}` (busca por Id)
    - `/catalogo/search` (paginado + filtros + highlight + spellcheck)
    - `/catalogo/suggest` (autocomplete)
    - `/queues` (dashboard HTML de filas)
    - `/queues/api/*` (APIs internas para o dashboard)

✔ Cria toda topologia RabbitMQ automaticamente  
✔ Processamento idempotente  
✔ Optimistic Locking baseado em versão  
✔ Criação de `unique_key` para evitar duplicidades  
✔ Indexação PRO no Elasticsearch  
✔ Highlight (HTML)  
✔ Correção ortográfica (sugestão de frases)  
✔ Busca fuzzy, range, sorting, pagination  
✔ Autocomplete (edge-ngram + phrase-prefix + wildcard fallback)
✔ Dashboard de monitoramento



**🔍 Busca PRO:**

*   Fuzzy search + correção ortográfica

*   Highlight com `<strong>`

*   Filtros por range de preço

*   Ordenação dinâmica

*   Paginação


**✨ Autocomplete:**

    http://localhost:8081/autocomplete.html

*   Prefix matching

*   Edge n-gram

*   Fuzzy fallback

*   De-duplicação

![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/autocomplete_1.png)

![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/autocomplete_2.png)

* * *

🐇 Mensageria RabbitMQ
----------------------

### Topologia

*   **Exchange Principal**: `products.exchange` (Topic)

*   **Dead Letter Exchange**: `products.dlx` (Direct)


### Filas

| Fila | Propósito | TTL |
| --- | --- | --- |
| `products.created.queue` | Eventos criação | \- |
| `products.updated.queue` | Eventos atualização | \- |
| `products.deleted.queue` | Eventos remoção | \- |
| `products.retry.5s.queue` | Retentativas | 5s |
| `products.dead.queue` | DLQ final | 14 dias |

### Fluxo Retry + DLQ

![Fluxo Retry DLQ](docs/diagrams/retry-dlq-flow.svg)

**Acesso UI:** `http://localhost:15672` (guest/guest)</br>
RabbitMQ PrintScreen:
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/rabbitMQ_1.png)

* * *

📊 Dashboard de Filas
---------------------

**URL:** `http://localhost:8081/queues`

**Funcionalidades:**

*   ✅ Visualização em tempo real (auto-refresh 5s)

*   ✅ Contadores de mensagens por fila

*   ✅ Peek das primeiras mensagens

*   ✅ Highlight JSON automático

*   ✅ Reprocessamento de mensagens

*   ✅ Exclusão de mensagens da DLQ


**Endpoints Internos:**

*   `GET /queues/api/all-messages` - JSON com mensagens

*   `POST /queues/reprocess` - Reprocessar mensagem

*   `POST /queues/delete` - Deletar mensagem DLQ

Visualização via web browser:</br>
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/dashboard-queues_1.png)

* * *

🗂 Gestão Elasticsearch
-----------------------

### Versionamento de Índices

*   **Índices físicos**: `products_v1`, `products_v2`, `products_v3`...

*   **Aliases permanentes**:

    *   `products_read` (consultas)

    *   `products_write` (indexação)


### Scripts Disponíveis
```bash
# Reset completo do índice
cd "microsB - Consumer"
sh scripts/reset-index.sh
```
PrintScreen:
![reset_index](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/script_reset-index.png)
```bash    
# Upgrade de versão (zero downtime)
sh scripts/upgrade-index.sh
```
PrintScreen:

![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/script_upgrade-index.png)

![Upgrade da indices](docs/diagrams/upgrade-index-flow.svg)

### Quando Usar Upgrade?

*   Mudança de analyzer

*   Alteração de mapping

*   Adição de novos campos


* * *

💾 Backup & Restore
-------------------

### PostgreSQL

**Backup:**
```bash
cd scripts
sh backup_postgres.sh
```
PrintScreen:
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/backup_postgresql.png)

**Restore:**
```bash
gunzip < postgres_backup_2025-11-17_14-00-00.sql.gz | docker exec -i postgres psql -U microsa microsa
```
PrintScreen:
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/restore_backup_postgresql.png)

### Elasticsearch

**Setup Snapshots:**
```bash
cd scripts
sh elastic_backup_setup.sh
sh backup_elasticsearch.sh
```
PrintScreen:
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/backup_Elasticsearch.png)

**Restore Interativo:**
```bash
sh elastic_restore_manager.sh
```
PrintScreen:
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/restore_backup_Elasticsearch_2.png)

**Restore Individual:**
```bash
sh restore_elasticsearch.sh snapshot_xxx
```
PrintScreen:
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/restore_backup_Elasticsearch_1.png)  
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/restore_backup_Elasticsearch_3.png)

**Listar Snapshots:**
```bash
curl -s http://localhost:9200/_snapshot/my_backup/_all?pretty
```
* * *

📚 Como Estudar Este Projeto
----------------------------

### 1\. **Entender a Arquitetura**

*   Leia os diagramas Mermaid

*   Acompanhe fluxo ponta a ponta


### 2\. **Executar o Pipeline**

*   Observe PostgreSQL → RabbitMQ → Elasticsearch

*   Monitore filas em `http://localhost:15672`


### 3\. **Testar Funcionalidades**

*   Busca fuzzy com erros de digitação

*   Autocomplete progressivo

*   Dashboard de filas em tempo real


### 4\. **Explorar Cenários de Erro**

*   Desligue serviços para ver resiliência

*   Force reprocessamento via DLQ

*   Teste concorrência com requests paralelos


### 5\. **Analisar Estratégias**

*   Idempotência e unique\_key

*   Optimistic locking

*   Retry patterns + DLX


* * *

🐛 Troubleshooting Comum
------------------------

### **Erro: "Queue não existe no startup"**

**Solução:** Execute Microserviço B primeiro (ele cria as filas)</br>
![](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/img/microsb_start_fail_1.png)

### **Erro: "Connection refused" no Elasticsearch**

**Solução:** Aguarde 30s após `docker compose up` para ES inicializar

### **Seed não executa**

**Solução:** Verifique se RabbitMQ está acessível na porta 15672

### **Mensagens presas na DLQ**

**Solução:** Use o dashboard `/queues` para reprocessar ou deletar

### **Índice não criado**

**Solução:** Execute `reset-index.sh` no Microserviço B

* * *

🚀 Como Evoluir Este Projeto
----------------------------

*   **🔁 Mensageria**: Substituir RabbitMQ por Kafka

*   **🔍 Search**: Migrar para OpenSearch

*   **📊 Observability**: Adicionar métricas com Micrometer + Prometheus

*   **🔒 Security**: Implementar Keycloak para autenticação

*   **📈 CDC**: Inserir Outbox Pattern com Debezium

*   **🧪 Testing**: Adicionar Spring Cloud Contract para testes de contrato

*   **🌐 Frontend**: Criar interface React/Vue para demonstração

*   **📦 Deployment**: Adicionar Kubernetes manifests


* * *

🔗 Links Úteis
--------------

*   **RabbitMQ UI**: `http://localhost:15672` (guest/guest)

*   **Kibana**: `http://localhost:5601`

*   Elasticsearch: `http://localhost:9200`

*   **PostgreSQL**: `localhost:5435` (user: microsa, pass: microsa)

*   **Postman**: [`postman_collection.json`](https://raw.githubusercontent.com/wekers/elasticsearch/refs/heads/main/Wekers-Elasticsearch-uServ-A-B.postman_collection.json)


* * *

📄 Licença / Uso
----------

MIT – Livre para estudos, melhorias e uso profissional.

Sinta‑se à vontade para:

*   clonar
*   alterar
*   adaptar para outros domínios (ex: catálogo de livros, filmes, etc.)


* * *


### 👉 Se este projeto te ajudou, uma ⭐ no repositório já vale um café. ☕🙂


* * *

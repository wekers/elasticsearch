#!/bin/bash

set -e

ES_URL="http://localhost:9200"
INDEX_BASE="products"

# ================================================
# 👉 Caminho absoluto baseado na pasta do script
# ================================================
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SETTINGS_FILE="$BASE_DIR/src/main/resources/elasticsearch/product-settings.json"

echo "🔍 Buscando versão atual..."
CURRENT_INDEX=$(curl -s "$ES_URL/_alias/products_read" | jq -r 'keys[]')

if [[ "$CURRENT_INDEX" == "null" ]]; then
  echo "⚠ Nenhum índice encontrado. Execute a aplicação uma vez para criar 'products_v1'."
  exit 1
fi

# Extrair versão v1, v2, v3...
CURRENT_VERSION=$(echo "$CURRENT_INDEX" | sed 's/.*_v//')
NEXT_VERSION=$((CURRENT_VERSION + 1))
NEW_INDEX="${INDEX_BASE}_v${NEXT_VERSION}"

echo "📄 Índice atual: $CURRENT_INDEX"
echo "🚀 Criando novo índice: $NEW_INDEX"

# ======================================================
# 1) Criar índice com settings básicos
# ======================================================
curl -s -X PUT "$ES_URL/$NEW_INDEX" \
  -H "Content-Type: application/json" \
  -d '{
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  }' > /dev/null
echo ""

# ======================================================
# 2) Aplicar mapping completo
# ======================================================
if [[ ! -f "$SETTINGS_FILE" ]]; then
  echo "❌ Mapping não encontrado:"
  echo "   $SETTINGS_FILE"
  exit 1
fi

echo "📦 Aplicando mapping completo..."
curl -s -X PUT "$ES_URL/$NEW_INDEX/_mapping" \
  -H "Content-Type: application/json" \
  --data-binary @"$SETTINGS_FILE" > /dev/null
echo ""

# ======================================================
# 3) Reindexar
# ======================================================
echo "📦 Reindexando dados..."
curl -s -X POST "$ES_URL/_reindex?wait_for_completion=true" \
  -H "Content-Type: application/json" \
  -d "{
    \"source\": { \"index\": \"$CURRENT_INDEX\" },
    \"dest\": { \"index\": \"$NEW_INDEX\" }
  }" > /dev/null
echo ""

# ======================================================
# 4) Atualizar aliases
# ======================================================
echo "🔄 Atualizando aliases..."
curl -s -X POST "$ES_URL/_aliases" \
  -H "Content-Type: application/json" \
  -d "{
    \"actions\": [
      { \"remove\": { \"index\": \"$CURRENT_INDEX\", \"alias\": \"products_read\" }},
      { \"remove\": { \"index\": \"$CURRENT_INDEX\", \"alias\": \"products_write\" }},
      { \"add\": { \"index\": \"$NEW_INDEX\", \"alias\": \"products_read\" }},
      { \"add\": { \"index\": \"$NEW_INDEX\", \"alias\": \"products_write\" }}
    ]
  }" > /dev/null
echo ""

# Aguardar um pouco para garantir que tudo está sincronizado
sleep 2

echo "✅ Upgrade concluído!"
echo "📖 Agora usando:"
echo "   → Leitura: products_read  → $NEW_INDEX"
echo "   → Escrita: products_write → $NEW_INDEX"
echo ""

# ======================================================
# 5) Verificar mapping aplicado
# ======================================================
echo "🔍 Verificando mapping..."
curl -s "$ES_URL/$NEW_INDEX/_mapping" | jq '.[].mappings.properties | {nameSpellClean: .nameSpellClean, nameSpell: .nameSpell}'
echo ""

echo "❗ Caso queira remover o índice antigo:"
echo "curl -X DELETE \"$ES_URL/$CURRENT_INDEX\""

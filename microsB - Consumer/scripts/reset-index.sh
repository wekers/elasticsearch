#!/bin/bash
set -e

ES_URL="http://localhost:9200"
INDEX="products_v1"
SETTINGS="src/main/resources/elasticsearch/product-settings.json"

echo "🔥 RESETANDO ÍNDICES DO CATÁLOGO..."

# 1. Remove aliases para evitar travas
echo "🔗 Removendo aliases antigos..."
curl -s -X POST "$ES_URL/_aliases" -H "Content-Type: application/json" -d '{
  "actions": [
    { "remove": { "index": "*", "alias": "products_read" }},
    { "remove": { "index": "*", "alias": "products_write" }}
  ]
}' > /dev/null || true

# 2. Remover TODOS índices products_*
echo "🗑️ Removendo índices antigos..."
curl -s "http://localhost:9200/_cat/indices/products_*?h=index" | \
while read line; do
  echo "   → Deletando índice: $line"
  curl -s -X DELETE "$ES_URL/$line" > /dev/null
done

sleep 1

# 3. Criar novo índice do zero
echo "🚀 Criando novo índice: $INDEX"
curl -s -X PUT "$ES_URL/$INDEX" \
  -H "Content-Type: application/json" \
  --data-binary @"$SETTINGS"

# 4. Criar aliases
echo "🔗 Criando aliases products_read e products_write..."
curl -s -X POST "$ES_URL/_aliases" \
  -H "Content-Type: application/json" \
  -d "{
    \"actions\": [
      { \"add\": { \"index\": \"$INDEX\", \"alias\": \"products_read\" }},
      { \"add\": { \"index\": \"$INDEX\", \"alias\": \"products_write\" }}
    ]
  }"

# 5. Validar
echo "🧪 Validando estado do índice..."
HEALTH=$(curl -s "$ES_URL/_cluster/health/$INDEX?pretty" | jq -r '.status')

if [[ "$HEALTH" == "green" ]]; then
  echo "✅ ÍNDICE OK (GREEN)"
else
  echo "❌ ÍNDICE PROBLEMA: $HEALTH"
fi

echo ""
echo "🏁 Reset concluído!"

#!/bin/bash
set -e

ES_URL="http://localhost:9200"
INDEX="products_v1"
SETTINGS_FILE="src/main/resources/elasticsearch/product-settings.json"
TEMP_SETTINGS="/tmp/product-settings-temp.json"

echo "🔥 RESETANDO ÍNDICES DO CATÁLOGO..."

# ✅ Cria settings temporário com replicas=0
echo "⚙️  Ajustando configurações para single-node..."
cat "$SETTINGS_FILE" | jq '.settings.index.number_of_replicas = 0' > "$TEMP_SETTINGS"

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
curl -s "$ES_URL/_cat/indices/products_*?h=index" | \
while read line; do
  if [[ ! -z "$line" ]]; then
    echo "   → Deletando índice: $line"
    curl -s -X DELETE "$ES_URL/$line" > /dev/null
  fi
done

sleep 2

# 3. Criar novo índice com settings corrigido
echo "🚀 Criando novo índice: $INDEX"
curl -s -X PUT "$ES_URL/$INDEX" \
  -H "Content-Type: application/json" \
  --data-binary @"$TEMP_SETTINGS"

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

# 5. Forçar GREEN status
echo "🎯 Ajustando réplicas para garantir GREEN..."
curl -s -X PUT "$ES_URL/$INDEX/_settings" \
  -H "Content-Type: application/json" \
  -d '{"index.number_of_replicas": 0}' > /dev/null

sleep 1

# 6. Validar
echo "🧪 Validando estado do índice..."
HEALTH=$(curl -s "$ES_URL/_cluster/health/$INDEX?pretty" | jq -r '.status')
UNASSIGNED=$(curl -s "$ES_URL/_cluster/health/$INDEX?pretty" | jq -r '.unassigned_shards')

# Limpar arquivo temporário
rm -f "$TEMP_SETTINGS"

if [[ "$HEALTH" == "green" && "$UNASSIGNED" == "0" ]]; then
  echo "✅ ÍNDICE OK (GREEN) - Unassigned shards: $UNASSIGNED"
else
  echo "❌ ÍNDICE PROBLEMA: $HEALTH (Unassigned shards: $UNASSIGNED)"
  exit 1
fi

echo ""
echo "🏁 Reset concluído!"
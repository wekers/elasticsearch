#!/bin/bash
set -e

ES_URL="http://localhost:9200"
INDEX="products_v1"

# ===============================
#  BASE_DIR seguro (independente de onde executar)
# ===============================
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SETTINGS_FILE="$BASE_DIR/src/main/resources/elasticsearch/product-settings.json"
TEMP_SETTINGS="/tmp/product-settings-temp.json"

echo "🔥 RESETANDO ÍNDICES DO CATÁLOGO..."

# 1. Ajustar réplicas temporariamente
echo "⚙️  Ajustando configurações para single-node..."

if [[ ! -f "$SETTINGS_FILE" ]]; then
  echo "❌ ERRO: Não encontrei o SETTINGS_FILE:"
  echo "   $SETTINGS_FILE"
  exit 1
fi

cat "$SETTINGS_FILE" | jq '.settings.index.number_of_replicas = 0' > "$TEMP_SETTINGS"

# 2. Remover aliases
echo "🔗 Removendo aliases antigos..."
curl -s -X POST "$ES_URL/_aliases" \
  -H "Content-Type: application/json" \
  -d '{
    "actions": [
      { "remove": { "index": "*", "alias": "products_read" }},
      { "remove": { "index": "*", "alias": "products_write" }}
    ]
  }' > /dev/null || true
echo

# 3. Remover índices antigos
echo "🗑️ Removendo índices antigos..."
curl -s "$ES_URL/_cat/indices/products_*?h=index" | while read line; do
  if [[ ! -z "$line" ]]; then
    echo "   → Deletando índice: $line"
    curl -s -X DELETE "$ES_URL/$line" > /dev/null
  fi
done
echo

sleep 2

# 4. Criar novo índice
echo "🚀 Criando novo índice: $INDEX"
curl -s -X PUT "$ES_URL/$INDEX" \
  -H "Content-Type: application/json" \
  --data-binary @"$TEMP_SETTINGS" -w "\n"

# 5. Criar aliases
echo "🔗 Criando aliases products_read e products_write..."
curl -s -X POST "$ES_URL/_aliases" \
  -H "Content-Type: application/json" \
  -d "{
    \"actions\": [
      { \"add\": { \"index\": \"$INDEX\", \"alias\": \"products_read\" }},
      { \"add\": { \"index\": \"$INDEX\", \"alias\": \"products_write\" }}
    ]
  }" -w "\n"

# 6. Ajustar replicas
echo "🎯 Ajustando réplicas para garantir GREEN..."
curl -s -X PUT "$ES_URL/$INDEX/_settings" \
  -H "Content-Type: application/json" \
  -d '{"index.number_of_replicas": 0}' > /dev/null
echo

sleep 1

# 7. Validar estado
echo "🧪 Validando estado do índice..."
HEALTH=$(curl -s "$ES_URL/_cluster/health/$INDEX" | jq -r '.status')
UNASSIGNED=$(curl -s "$ES_URL/_cluster/health/$INDEX" | jq -r '.unassigned_shards')

rm -f "$TEMP_SETTINGS"

if [[ "$HEALTH" == "green" && "$UNASSIGNED" == "0" ]]; then
  echo "✅ ÍNDICE OK (GREEN) - Unassigned shards: $UNASSIGNED"
else
  echo "❌ ÍNDICE PROBLEMA: $HEALTH (Unassigned shards: $UNASSIGNED)"
  exit 1
fi

echo
echo "🏁 Reset concluído!"

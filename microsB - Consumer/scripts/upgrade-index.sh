#!/bin/bash

set -e

ES_URL="http://localhost:9200"
INDEX_BASE="products"
SETTINGS_FILE="src/main/resources/elasticsearch/product-settings.json"

echo "🔍 Buscando versão atual..."
CURRENT_INDEX=$(curl -s "$ES_URL/_alias/products_read" | jq -r 'keys[]')

if [[ "$CURRENT_INDEX" == "null" ]]; then
  echo "⚠ Nenhum índice encontrado. Execute a aplicação uma vez para criar 'products_v1'."
  exit 1
fi

CURRENT_VERSION=$(echo $CURRENT_INDEX | sed 's/.*_v//')
NEXT_VERSION=$((CURRENT_VERSION + 1))
NEW_INDEX="${INDEX_BASE}_v${NEXT_VERSION}"

echo "📄 Índice atual: $CURRENT_INDEX"
echo "🚀 Criando novo índice: $NEW_INDEX"

# ✅ CORREÇÃO: Criar índice primeiro com settings básicos
curl -s -X PUT "$ES_URL/$NEW_INDEX" -H "Content-Type: application/json" -d '{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  }
}' > /dev/null

echo "📦 Aplicando mapping completo..."

# ✅ CORREÇÃO: Aplicar mapping separadamente
curl -s -X PUT "$ES_URL/$NEW_INDEX/_mapping" \
  -H "Content-Type: application/json" \
  --data-binary @"$SETTINGS_FILE" > /dev/null

echo "📦 Reindexando dados..."

curl -s -X POST "$ES_URL/_reindex?wait_for_completion=true" \
  -H "Content-Type: application/json" \
  -d "{
    \"source\": { \"index\": \"$CURRENT_INDEX\" },
    \"dest\": { \"index\": \"$NEW_INDEX\" }
  }" > /dev/null

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

# ✅ CORREÇÃO: Aguardar um pouco para garantir que tudo está sincronizado
sleep 2

echo ""
echo "✅ Upgrade concluído!"
echo "📖 Agora usando:"
echo "   → Leitura: products_read → $NEW_INDEX"
echo "   → Escrita: products_write → $NEW_INDEX"

# ✅ CORREÇÃO: Verificar se o mapping foi aplicado corretamente
echo ""
echo "🔍 Verificando mapping..."
curl -s -X GET "$ES_URL/$NEW_INDEX/_mapping" | jq '.[].mappings.properties | {nameSpellClean: .nameSpellClean, nameSpell: .nameSpell}'

echo ""
echo "❗ Caso queira remover o índice antigo:"
echo "curl -X DELETE \"$ES_URL/$CURRENT_INDEX\""
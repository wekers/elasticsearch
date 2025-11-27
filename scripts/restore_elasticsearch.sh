#!/bin/bash

# ------------------------------------------------------------
# Restore snapshot com filtro de índices de dados apenas
# Uso:
#   ./restore_elasticsearch.sh snapshot_2025-11-26_22-45-04
# ------------------------------------------------------------

if [ -z "$1" ]; then
    echo "Uso: ./restore_elasticsearch.sh <snapshot_name>"
    exit 1
fi

SNAPSHOT_NAME="$1"
ES_URL="http://localhost:9200"
REPO="my_backup"

echo "🔍 Buscando índices dentro do snapshot: $SNAPSHOT_NAME"

# --- Captura os índices existentes dentro do snapshot ---
INDICES=$(curl -s "${ES_URL}/_snapshot/${REPO}/${SNAPSHOT_NAME}" \
    | jq -r '.snapshots[0].indices | join(",")')

if [ -z "$INDICES" ] || [ "$INDICES" = "null" ]; then
    echo "❌ Não foi possível capturar índices do snapshot!"
    exit 1
fi

echo "📦 Todos os índices no snapshot:"
echo "$INDICES"
echo

# --- Filtra apenas índices de dados (exclui system indices) ---
DATA_INDICES=$(echo "$INDICES" | tr ',' '\n' | grep -v '^\..*' | tr '\n' ',' | sed 's/,$//')

if [ -z "$DATA_INDICES" ]; then
    echo "❌ Nenhum índice de dados encontrado no snapshot!"
    echo "   (Apenas índices do sistema foram detectados)"
    exit 1
fi

echo "🎯 Índices de dados filtrados para restore:"
echo "$DATA_INDICES"
echo

# Monta o JSON dinamicamente
DATA=$(jq -n \
    --arg indices "$DATA_INDICES" \
    '{indices: $indices, include_global_state: false}')

echo "♻️ Iniciando restore dos índices de dados..."
echo "🔧 Enviando payload:"
echo "$DATA"
echo

# --- Executa o restore ---
RESPONSE=$(curl -s -w "%{http_code}" -X POST "${ES_URL}/_snapshot/${REPO}/${SNAPSHOT_NAME}/_restore" \
  -H "Content-Type: application/json" \
  -d "$DATA")

HTTP_CODE=${RESPONSE: -3}
RESPONSE_BODY=${RESPONSE%???}

echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"

if [ "$HTTP_CODE" -eq 200 ]; then
    echo
    echo "✅ Restore iniciado com sucesso!"
    echo "📡 Acompanhe progresso em:"
    echo "   → ${ES_URL}/_cat/recovery?v"
    echo "   → ${ES_URL}/_cluster/health?pretty"
else
    echo
    echo "❌ Erro no restore (HTTP $HTTP_CODE)"
fi
#!/bin/bash

# Restore single snapshot
# ie of use: ./scripts/restore_elasticsearch.sh snapshot_2025-11-18_21-30-2

if [ -z "$1" ]; then
    echo "Uso: ./restore_elasticsearch.sh <snapshot_name>"
    exit 1
fi

DATA='{
  "indices": "products_v1",
  "include_global_state": false
}'

SNAPSHOT_NAME="$1"

echo "♻️ Restaurando snapshot: ${SNAPSHOT_NAME}"

curl -X POST "http://localhost:9200/_snapshot/my_backup/${SNAPSHOT_NAME}/_restore" \
  -H "Content-Type: application/json" \
  -d "$DATA"

echo "✅ Restore iniciado."
echo "⚠️ Verifique progresso em: http://localhost:9200/_cat/recovery?v"

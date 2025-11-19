#!/bin/bash
# 2 - make snapshot backup
set -e

SNAPSHOT_NAME="snapshot_$(date +%Y-%m-%d_%H-%M-%S)"

echo "📦 Iniciando snapshot: ${SNAPSHOT_NAME}"

curl -X PUT "http://localhost:9200/_snapshot/my_backup/${SNAPSHOT_NAME}?wait_for_completion=true" \
  -H "Content-Type: application/json" \
  -d '{
    "indices": "*",
    "ignore_unavailable": true,
    "include_global_state": true
  }'

echo "✅ Snapshot concluído: ${SNAPSHOT_NAME}"
echo "📁 Local: docker volume 'essnapshot'"

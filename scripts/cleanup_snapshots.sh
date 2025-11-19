#!/bin/bash
# 3 - Clear snapshots
set -e

# manter apenas os últimos 7 snapshots
KEEP_LAST=7

echo "🧹 Limpando snapshots antigos..."

# lista snapshots do mais recente para o mais antigo
SNAPSHOTS=($(curl -s "http://localhost:9200/_snapshot/my_backup/_all" | jq -r '.snapshots[].snapshot'))

TOTAL=${#SNAPSHOTS[@]}

if [ "$TOTAL" -le "$KEEP_LAST" ]; then
  echo "Nenhum snapshot para remover. Total: $TOTAL"
  exit 0
fi

# apagar snapshots antigos
COUNT_TO_DELETE=$((TOTAL - KEEP_LAST))

echo "Removendo $COUNT_TO_DELETE snapshots antigos..."

for ((i=0; i<$COUNT_TO_DELETE; i++)); do
  NAME="${SNAPSHOTS[$i]}"
  echo "🗑️ Deletando: $NAME"
  curl -X DELETE "http://localhost:9200/_snapshot/my_backup/$NAME"
done

echo "✅ Cleanup concluído."

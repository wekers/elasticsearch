#!/bin/bash
# 1 - Run this script first
set -e

echo "📁 Registrando repositório de snapshots do Elasticsearch..."

curl -X PUT "http://localhost:9200/_snapshot/my_backup" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "fs",
    "settings": {
      "location": "/usr/share/elasticsearch/snapshots",
      "compress": true
    }
  }'

echo "✅ Repositório criado com sucesso!"

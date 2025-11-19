#!/usr/bin/env bash

# ================================
#  RESTORE MANAGER PRO — Elasticsearch
#  Interactive Menu with fzf (fallback simples)
#  by Fernando with help of ChatGPT 😎
# ================================

ES_URL="http://localhost:9200"
REPO="my_backup"
INDEX="products_v1"

GREEN="\e[32m"
YELLOW="\e[33m"
RED="\e[31m"
BLUE="\e[34m"
NC="\e[0m"

echo -e "${BLUE}📦 RESTORE MANAGER PRO – Elasticsearch${NC}"

# ================================
#   CHECK SNAPSHOT REPOSITORY
# ================================
echo -e "${YELLOW}🔍 Verificando repositório '${REPO}'...${NC}"

if ! curl -s "$ES_URL/_snapshot/${REPO}" | grep -q "\"${REPO}\""; then
  echo -e "${RED}❌ ERRO: Repositório '${REPO}' não existe.${NC}"
  echo -e "Execute antes: ./scripts/elastic_backup_setup.sh"
  exit 1
fi

# ================================
#   FETCH SNAPSHOTS
# ================================
echo -e "${YELLOW}📥 Obtendo lista de snapshots...${NC}"

SNAPSHOTS=$(curl -s "$ES_URL/_snapshot/${REPO}/_all" | jq -r '.snapshots[].snapshot')

if [ -z "$SNAPSHOTS" ]; then
  echo -e "${RED}❌ Nenhum snapshot encontrado.${NC}"
  exit 1
fi

# ================================
#   SELECT SNAPSHOT
# ================================
choose_snapshot() {
  echo -e "${YELLOW}📌 Escolha um snapshot:${NC}"

  if command -v fzf &>/dev/null; then
    SELECTED=$(echo "$SNAPSHOTS" | fzf --prompt="Snapshot → " --height=40%)
  else
    echo -e "${BLUE}FZF não instalado. Usando menu simples.${NC}"
    select s in $SNAPSHOTS; do
      SELECTED="$s"
      break
    done
  fi
}

choose_snapshot

if [ -z "$SELECTED" ]; then
  echo -e "${RED}❌ Nenhum snapshot selecionado. Abortando.${NC}"
  exit 1
fi

echo -e "${GREEN}✔ Snapshot escolhido: ${SELECTED}${NC}"

# ================================
#   SHOW SNAPSHOT DETAILS
# ================================
echo -e "${YELLOW}📊 Detalhes do snapshot:${NC}"

curl -s "$ES_URL/_snapshot/${REPO}/${SELECTED}?pretty" | sed 's/^/   /'

echo ""

# ================================
#   RESTORE MODE
# ================================
echo -e "${YELLOW}📌 Escolha o modo de restore:${NC}"
echo "1) Restaurar apenas índice: ${INDEX}"
echo "2) Restaurar snapshot COMPLETO (não recomendado!)"
echo "3) Cancelar"

read -rp "Opção (1-3): " MODE

if [ "$MODE" == "3" ]; then
  echo -e "${RED}❌ Cancelado.${NC}"
  exit 0
fi

# ================================
#   CONFIRMATION
# ================================
echo -e "${RED}⚠ ATENÇÃO: ESTA AÇÃO É IRREVERSÍVEL!${NC}"
read -rp "Confirmar restore do snapshot '${SELECTED}'? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
  echo -e "${RED}❌ Restore cancelado.${NC}"
  exit 0
fi

# ================================
#   DELETE TARGET INDEX (SAFE)
# ================================
if [ "$MODE" == "1" ]; then
  echo -e "${YELLOW}🧹 Removendo índice '${INDEX}'...${NC}"
  curl -s -X DELETE "$ES_URL/${INDEX}"
fi

# ================================
#   EXECUTE RESTORE
# ================================
echo -e "${BLUE}♻ Restaurando snapshot ${SELECTED}...${NC}"

if [ "$MODE" == "1" ]; then
  BODY=$(cat <<EOF
{
  "indices": "${INDEX}",
  "include_global_state": false
}
EOF
)
else
  BODY=$(cat <<EOF
{
  "include_global_state": true
}
EOF
)
fi

curl -s -X POST "$ES_URL/_snapshot/${REPO}/${SELECTED}/_restore" \
     -H "Content-Type: application/json" \
     -d "${BODY}" | jq

echo -e "${GREEN}✔ Restore iniciado.${NC}"
echo -e "👉 Verifique progresso em: ${BLUE}${ES_URL}/_cat/recovery?v${NC}"

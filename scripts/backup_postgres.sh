#!/bin/bash

CONTAINER_NAME="postgres"
DB_NAME="microsa"
DB_USER="microsa"
BACKUP_DIR="./backups"
LOG_DIR="./logs"
RETENTION_DAYS=7
DATE=$(date +"%Y-%m-%d_%H-%M-%S")
FILE="${BACKUP_DIR}/postgres_backup_${DATE}.sql.gz"
LOG="${LOG_DIR}/backup.log"

mkdir -p "${BACKUP_DIR}"
mkdir -p "${LOG_DIR}"

echo "[$(date)] Iniciando backup..." | tee -a "${LOG}"

if docker exec -i ${CONTAINER_NAME} pg_dump -U ${DB_USER} ${DB_NAME} | gzip > "${FILE}"; then
  echo "[$(date)] Backup ok: ${FILE}" | tee -a "${LOG}"
else
  echo "[$(date)] ERRO ao gerar backup!" | tee -a "${LOG}"
fi

echo "[$(date)] Limpando backups com mais de ${RETENTION_DAYS} dias..." | tee -a "${LOG}"
find "${BACKUP_DIR}" -type f -mtime +${RETENTION_DAYS} -delete

echo "[$(date)] Finalizado." | tee -a "${LOG}"

#!/bin/bash
# ============================================================
# Football AI — Stop script
# ============================================================
# Bu skript barcha servislarni to'xtatadi va portlarni bo'shatadi.
# Volume'lar (ma'lumotlar) saqlanib qoladi.
#
# Ishlatish:  bash stop.sh
# ============================================================

YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

echo -e "${YELLOW}▶ Servislarni to'xtatish...${NC}"

# docker-compose orqali
if command -v docker-compose &> /dev/null; then
    docker-compose down --remove-orphans 2>/dev/null || true
elif docker compose version &> /dev/null; then
    docker compose down --remove-orphans 2>/dev/null || true
fi

# Konteynerlarni nom bo'yicha to'xtatish
CONTAINERS=(football-ai-backend football-ai-frontend football-ai-db football-ai-redis football-ai-minio football-ai-minio-init)
for C in "${CONTAINERS[@]}"; do
    if docker ps -aq -f name=^${C}$ | grep -q .; then
        docker rm -f $C 2>/dev/null || true
    fi
done

# Portlarni bo'shatish
for PORT in 8060 8069 9100 9101 5432 6379; do
    PIDS=$(lsof -ti:$PORT 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
        kill -9 $PIDS 2>/dev/null || true
    fi
done

echo -e "${GREEN}✓ Hammasi to'xtatildi${NC}"
echo -e "  Qaytadan ishga tushirish: ${YELLOW}bash restart.sh${NC}"

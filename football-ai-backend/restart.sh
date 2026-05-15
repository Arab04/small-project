#!/bin/bash
# ============================================================
# Football AI — Restart script
# CPU Server: 31.184.242.73
# ============================================================
# Bu skript:
#   1. Kerakli portlardagi (8060, 8069, 9100, 9101) eski jarayonlarni to'xtatadi
#   2. Eski Docker konteynerlarni to'xtatadi (zudlik bilan)
#   3. Loyihani qaytadan build qiladi (kerakli o'zgarishlar bo'lsa)
#   4. Hammasini ishga tushiradi
#   5. Health check qiladi
#
# Ishlatish:  bash restart.sh
#             bash restart.sh --clean   (eski image va volume'larni ham o'chiradi)
# ============================================================

set -e   # Birinchi xatoda to'xtash

# Ranglar (terminal'da chiroyli ko'rinishi uchun)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Football AI — Restart & Deploy Script                  ║${NC}"
echo -e "${BLUE}║   Server: 31.184.242.73                                  ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================================
# 1. .env faylini tekshirish
# ============================================================
if [ ! -f .env ]; then
    echo -e "${RED}✗ .env fayl topilmadi!${NC}"
    echo -e "${YELLOW}  Bajaring: cp .env.example .env va to'ldiring${NC}"
    exit 1
fi
echo -e "${GREEN}✓ .env fayl topildi${NC}"

# ============================================================
# 2. Kerakli komandalarni tekshirish
# ============================================================
for cmd in docker docker-compose; do
    if ! command -v $cmd &> /dev/null; then
        # docker compose (yangi versiya) bilan ham urinib ko'ramiz
        if [ "$cmd" = "docker-compose" ]; then
            if ! docker compose version &> /dev/null; then
                echo -e "${RED}✗ '$cmd' topilmadi va 'docker compose' ham ishlamaydi${NC}"
                exit 1
            fi
            # Yangi versiyani ishlatamiz
            COMPOSE="docker compose"
        else
            echo -e "${RED}✗ '$cmd' o'rnatilmagan${NC}"
            exit 1
        fi
    else
        if [ "$cmd" = "docker-compose" ]; then
            COMPOSE="docker-compose"
        fi
    fi
done
echo -e "${GREEN}✓ docker va docker compose mavjud${NC}"

# ============================================================
# 3. Portlardagi eski jarayonlarni to'xtatish
# ============================================================
echo ""
echo -e "${YELLOW}▶ Eski jarayonlarni to'xtatish...${NC}"

PORTS_TO_KILL=(8060 8069 9100 9101 5432 6379)
for PORT in "${PORTS_TO_KILL[@]}"; do
    PIDS=$(lsof -ti:$PORT 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
        echo -e "  ${YELLOW}Port $PORT — to'xtatilmoqda (PIDs: $PIDS)${NC}"
        # Avval SIGTERM, keyin SIGKILL
        kill -TERM $PIDS 2>/dev/null || true
        sleep 2
        # Hali ham tirik bo'lsa - majburiy o'chirish
        STILL_ALIVE=$(lsof -ti:$PORT 2>/dev/null || true)
        if [ -n "$STILL_ALIVE" ]; then
            kill -9 $STILL_ALIVE 2>/dev/null || true
        fi
    else
        echo -e "  Port $PORT — bo'sh"
    fi
done

# ============================================================
# 4. Eski Docker konteynerlarni to'xtatish
# ============================================================
echo ""
echo -e "${YELLOW}▶ Eski Docker konteynerlarni to'xtatish...${NC}"

# docker-compose down — local docker-compose orqali yaratilganlarni
$COMPOSE down --remove-orphans 2>/dev/null || true

# Eski konteynerlarni nom bo'yicha o'chirish (agar qolgan bo'lsa)
OLD_CONTAINERS=(
    football-ai-backend
    football-ai-frontend
    football-ai-db
    football-ai-redis
    football-ai-minio
    football-ai-minio-init
)
for CONTAINER in "${OLD_CONTAINERS[@]}"; do
    if docker ps -aq -f name=^${CONTAINER}$ | grep -q .; then
        echo -e "  ${YELLOW}Konteyner $CONTAINER — o'chirilmoqda${NC}"
        docker rm -f $CONTAINER 2>/dev/null || true
    fi
done

# ============================================================
# 5. --clean bayrog'i: image va volume'larni o'chirish
# ============================================================
if [ "$1" = "--clean" ]; then
    echo ""
    echo -e "${YELLOW}▶ --clean: image va volume'larni o'chirish...${NC}"

    # Image'larni o'chirish
    docker images | grep "football-ai" | awk '{print $3}' | xargs -r docker rmi -f 2>/dev/null || true

    # Volume'lar
    read -p "DB va MinIO ma'lumotlarini ham o'chirasizmi? (yes/no): " ANSWER
    if [ "$ANSWER" = "yes" ]; then
        docker volume rm football-ai_postgres_data football-ai_redis_data football-ai_minio_data 2>/dev/null || true
        echo -e "${GREEN}  ✓ Volume'lar o'chirildi${NC}"
    else
        echo -e "  Volume'lar saqlandi"
    fi
fi

# ============================================================
# 6. Frontend papkasini tekshirish (docker-compose'da kerak)
# ============================================================
FRONTEND_PATH="../football-ai-frontend"
if [ ! -d "$FRONTEND_PATH" ]; then
    echo ""
    echo -e "${YELLOW}⚠ Frontend papkasi topilmadi: $FRONTEND_PATH${NC}"
    echo -e "${YELLOW}  docker-compose.yml frontend service'i ishga tushmasligi mumkin.${NC}"
    echo -e "${YELLOW}  Agar frontend kerak bo'lsa, uni ham loyiha papkasiga qo'ying.${NC}"
fi

# ============================================================
# 7. Loyihani build qilib ishga tushirish
# ============================================================
echo ""
echo -e "${BLUE}▶ Servislarni build qilish va ishga tushirish...${NC}"
$COMPOSE up -d --build

# ============================================================
# 8. Health check
# ============================================================
echo ""
echo -e "${YELLOW}▶ Servislar tayyor bo'lishini kutish...${NC}"
sleep 5

# Backend health check (max 90 sekund kutamiz)
echo -e "  Backend health check..."
ATTEMPTS=0
MAX_ATTEMPTS=30
while [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
    if curl -sf http://localhost:8060/api/actuator/health > /dev/null 2>&1; then
        echo -e "  ${GREEN}✓ Backend ishlaydi (http://31.184.242.73:8060/api)${NC}"
        break
    fi
    ATTEMPTS=$((ATTEMPTS + 1))
    sleep 3
    printf "."
done
if [ $ATTEMPTS -eq $MAX_ATTEMPTS ]; then
    echo ""
    echo -e "  ${RED}✗ Backend tayyor bo'lmadi (90 sekund)${NC}"
    echo -e "  ${YELLOW}Loglarni ko'ring: $COMPOSE logs backend${NC}"
fi

# Frontend health check
echo -e "  Frontend health check..."
sleep 2
if curl -sf http://localhost:8069/ > /dev/null 2>&1; then
    echo -e "  ${GREEN}✓ Frontend ishlaydi (http://31.184.242.73:8069)${NC}"
else
    echo -e "  ${YELLOW}⚠ Frontend hali tayyor emas — bir necha sekund kuting${NC}"
fi

# MinIO check
if curl -sf http://localhost:9100/minio/health/live > /dev/null 2>&1; then
    echo -e "  ${GREEN}✓ MinIO ishlaydi (S3 API: 9100, Console: 9101)${NC}"
else
    echo -e "  ${YELLOW}⚠ MinIO hali tayyor emas${NC}"
fi

# ============================================================
# 9. Yakuniy ma'lumot
# ============================================================
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                  ✓ DEPLOY TUGADI                         ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  Frontend:    http://31.184.242.73:8069                  ║${NC}"
echo -e "${GREEN}║  Backend API: http://31.184.242.73:8060/api              ║${NC}"
echo -e "${GREEN}║  Swagger UI:  http://31.184.242.73:8060/api/swagger-ui.html${NC}"
echo -e "${GREEN}║  MinIO API:   http://31.184.242.73:9100                  ║${NC}"
echo -e "${GREEN}║  MinIO UI:    http://31.184.242.73:9101                  ║${NC}"
echo -e "${GREEN}║    Login:     minioadmin / minioadmin123                 ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  Loglar:                                                 ║${NC}"
echo -e "${GREEN}║    $COMPOSE logs -f backend                          ║${NC}"
echo -e "${GREEN}║    $COMPOSE logs -f frontend                         ║${NC}"
echo -e "${GREEN}║  To'xtatish:                                             ║${NC}"
echo -e "${GREEN}║    $COMPOSE down                                     ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"

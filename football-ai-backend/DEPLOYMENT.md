# Football AI — Deployment Guide

## Server konfiguratsiyasi

### CPU Server (31.184.242.73)
| Xizmat | Port | URL |
|--------|------|-----|
| Frontend (nginx) | 8088 | http://31.184.242.73:8088 |
| Backend (Spring Boot) | 8080 | http://31.184.242.73:8080/api |
| Backend healthcheck | 8080 | http://31.184.242.73:8080/api/actuator/health |
| Swagger UI | 8080 | http://31.184.242.73:8080/api/swagger-ui.html |
| MinIO S3 API | 9100 | http://31.184.242.73:9100 |
| MinIO Console | 9101 | http://31.184.242.73:9101 |
| PostgreSQL | 5432 | Docker internal only |
| Redis | 6379 | Docker internal only |

### RunPod GPU Pod (6uunrr39bj4wqp)
| Xizmat | URL |
|--------|-----|
| FastAPI ML Pipeline | https://0bhseo1o53k5wh-8001.proxy.runpod.net |
| ML Healthcheck | https://0bhseo1o53k5wh-8001.proxy.runpod.net/health |

---

## Tezkor ishga tushirish

### 1. Birinchi marta deploy

```bash
# Loyihani kerakli papkaga ko'chiring
cd /opt
git clone <your-repo> football-ai && cd football-ai

# .env faylini tekshiring (allaqachon real qiymatlar bilan tayyor)
cat .env

# Hammasini build qilib ishga tushiring
bash restart.sh
```

### 2. Qayta ishga tushirish (kunlik)

```bash
cd /opt/football-ai
bash restart.sh
```

### 3. To'xtatish

```bash
bash stop.sh
```

### 4. To'liq tozalab qayta o'rnatish

```bash
bash restart.sh --clean
# DB ma'lumotlarini o'chirishga ham rozilik berasiz/bermaysiz
```

---

## Papka tuzilishi

```
/opt/
├── football-ai/                 # Backend papkasi (bu loyiha)
│   ├── .env                     # Sezgir qiymatlar (real production)
│   ├── .env.example             # Template
│   ├── docker-compose.yml       # Production stack
│   ├── docker-compose.infra.yml # Faqat DB/Redis/MinIO (dev uchun)
│   ├── Dockerfile               # Backend image
│   ├── restart.sh               # Qayta ishga tushirish skripti
│   ├── stop.sh                  # To'xtatish skripti
│   └── src/
└── football-ai-frontend/        # Frontend papkasi (yonida turishi kerak!)
    ├── .env
    ├── Dockerfile
    ├── nginx.conf
    └── src/
```

> **DIQQAT:** Hozirgi `docker-compose.yml` frontend papkasi `../football-ai-frontend` da turishini kutadi.
> Agar boshqa joyda bo'lsa, `docker-compose.yml` da `build.context` ni o'zgartiring.

---

## Loglarni ko'rish

```bash
# Backend loglar (jonli)
docker compose logs -f backend

# Frontend (nginx) loglar
docker compose logs -f frontend

# Barcha servislar
docker compose logs -f

# Faqat oxirgi 100 qator
docker compose logs --tail=100 backend
```

---

## Portlardagi muammolarni hal qilish

Agar portlardan biri band bo'lib qolsa:

```bash
# Qaysi jarayon portni egallaganini ko'rish
sudo lsof -i:8080
sudo lsof -i:8088
sudo lsof -i:9100

# Majburiy o'chirish
sudo kill -9 <PID>

# Yoki bizning skript orqali (avtomatik tozalaydi)
bash restart.sh
```

---

## Test qilish

### 1. Backend health
```bash
curl http://31.184.242.73:8080/api/actuator/health
# Kutilgan javob: {"status":"UP",...}
```

### 2. Backend API
```bash
# Yangi klub ro'yxatdan o'tkazish (test)
curl -X POST http://31.184.242.73:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Test Admin",
    "email": "admin@test.uz",
    "password": "test123456",
    "phoneNumber": "+998901234567",
    "clubName": "Test Klub",
    "clubCity": "Toshkent"
  }'
```

### 3. Frontend
Brauzerda oching: http://31.184.242.73:8088

### 4. ML Service (RunPod)
```bash
curl https://0bhseo1o53k5wh-8001.proxy.runpod.net/health
```

### 5. MinIO Console
Brauzerda: http://31.184.242.73:9101
Login: `minioadmin` / `minioadmin123`

---

## Tez-tez uchraydigan muammolar

### Backend ishga tushmayapti, "Connection refused to postgres"
```bash
# DB konteyner ishlayotganini tekshiring
docker ps | grep postgres
docker compose logs postgres
# Healthy bo'lsa - backend qayta urinib ko'radi (depends_on bilan)
```

### Frontend backend'ga ulana olmayapti
- `.env`'da `APP_CORS_ALLOWED_ORIGINS` to'g'rimi tekshiring
- Brauzerda DevTools → Network → CORS xatolar bo'lsa, CORS sozlamasiga muammo

### MinIO bucket topilmadi
```bash
# minio-init konteynerini qayta ishga tushiring
docker compose up minio-init
```

### "JWT secret too short" xatosi
`.env`da `JWT_SECRET` kamida 32 bayt bo'lishi kerak:
```bash
openssl rand -base64 64
```

### RunPod ML so'rovi taymautga uchradi
- RunPod pod turg'unmi? Console'da tekshiring
- `RUNPOD_ENABLED=true` qilingan bo'lsa, backend pod'ni auto-start qiladi
- `RUNPOD_START_TIMEOUT=180` (3 daqiqa) cold start uchun yetarli bo'lishi kerak

---

## Production tavsiyalari

1. **HTTPS qo'shish:** nginx'ga Let's Encrypt sertifikati bilan SSL qo'shing
2. **Domen sozlash:** IP o'rniga `football-ai.uz` kabi domen ishlating
3. **Backup:** PostgreSQL volume'ini har kuni backup qiling (cron):
   ```bash
   docker exec football-ai-db pg_dump -U football_admin football_ai > /backup/db_$(date +%Y%m%d).sql
   ```
4. **MinIO secret kalitlarni yangilang:** `.env`'da `minioadmin123` ni kuchli parol bilan almashtiring
5. **Firewall:** UFW yoki iptables orqali faqat kerakli portlarni oching:
   ```bash
   sudo ufw allow 8088    # Frontend
   sudo ufw allow 8080    # Backend (yoki proxy orqali yopib qo'ying)
   sudo ufw allow 9100    # MinIO API (RunPod uchun)
   sudo ufw deny 5432     # PostgreSQL - tashqaridan kerak emas
   sudo ufw deny 6379     # Redis - tashqaridan kerak emas
   ```

---

## API endpoint'lar ro'yxati

- **Auth:** `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`
- **User:** `GET /api/users/me`
- **Matches:** `GET/POST/PUT/DELETE /api/matches`
- **Teams:** `GET /api/teams`
- **Opponents:** `GET/POST /api/opponents`
- **Video upload:** `POST /api/matches/{id}/video`
- **Analysis:** `POST /api/matches/{id}/analysis/start`
- **Reports:** `POST /api/reports/{id}/generate-pdf`
- **Dashboard:** `GET /api/dashboard`
- **ML callback (internal):** `POST /api/internal/ml-callback`

Batafsil: http://31.184.242.73:8080/api/swagger-ui.html

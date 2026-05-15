# football.ai Frontend

Futbol o'yin video tahlili uchun React + Vite frontend. Qisqa klipler (5-10 daqiqa) uchun mo'ljallangan.

## Tech stack

- **React 18** + **Vite 5** — fast dev server
- **Tailwind CSS** — custom dark theme (lime electric accent)
- **TanStack Query** — server state, caching, polling
- **Zustand** — auth store
- **React Router 6** — routing
- **Recharts** — momentum chart
- **Axios** — HTTP client

## Arxitektura

```
┌─────────────────────────────────────────────────┐
│  CPU SERVER                                     │
│                                                 │
│  ┌───────────────┐         ┌──────────────┐     │
│  │  Frontend     │────────▶│  Spring Boot │     │
│  │  (Vite/React) │  /api   │   :8080      │     │
│  │   :5173       │         └──────┬───────┘     │
│  └───────────────┘                │             │
│                                   │             │
└───────────────────────────────────┼─────────────┘
                                    │
                                    ▼
                           ┌─────────────────┐
                           │  RUNPOD GPU     │
                           │  ML Service     │
                           │  (FastAPI)      │
                           │   :8001         │
                           └─────────────────┘
```

## O'rnatish

### 1. Dependencies

```bash
cd football-ai-frontend
npm install
```

### 2. Environment

`.env` faylini yarating (loyiha root'da):

```bash
# Spring Boot backend manzili
VITE_API_URL=http://localhost:8080
```

Production uchun:
```bash
VITE_API_URL=https://api.yourdomain.com
```

### 3. Dev server

```bash
npm run dev
```

→ Brauzer: `http://localhost:5173`

Vite dev server `/api/*` so'rovlarni avtomatik `http://localhost:8080`ga proxy qiladi (vite.config.js).

### 4. Production build

```bash
npm run build
```

`dist/` papkasini nginx orqali serverda joylash:

```nginx
server {
    listen 80;
    server_name football.yourdomain.com;
    root /var/www/football-ai-frontend/dist;
    index index.html;

    # SPA — barcha route'lar index.html'ga
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Backend API proxy
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # Qisqa klip upload uchun (max 500 MB)
        client_max_body_size 500M;
        proxy_read_timeout 3600;
        proxy_send_timeout 3600;
    }
}
```

## Sahifalar

| Path | Sahifa | Holat |
|------|--------|-------|
| `/login` | Login | ✅ Tayyor |
| `/register` | Klub ro'yxati | ✅ Tayyor |
| `/` | Dashboard (overview) | ✅ Tayyor |
| `/matches` | O'yinlar ro'yxati | ✅ Tayyor |
| `/matches/new` | Yangi o'yin formi | ✅ Tayyor |
| `/matches/:id` | Match Detail (asosiy) | ✅ Tayyor |
| `/matches/:id/upload` | Video upload + calibration | ✅ Tayyor |
| `/matches/:id/report` | PDF hisobot | 🔧 Stub |
| `/matches/:id/players` | O'yinchilar ro'yxati | 🔧 Stub |
| `/teams` | Jamoalar | 🔧 Stub |
| `/reports` | Hisobotlar arxivi | 🔧 Stub |
| `/claude` | Claude AI chat | 🔧 Stub |
| `/settings` | Sozlamalar | 🔧 Stub |

## Asosiy komponentlar

### Match Detail page (`/matches/:id`)

To'liq tahlil dashboard'i:

- **MatchHero** — jamoa nomi, hisob, sana
- **MetricsRow** — 4 ta yuqori statistika (possession, sprints, distance, top speed)
- **MomentumChart** — 5-min snapshot timeline (Recharts AreaChart)
- **PeriodStaminaSplit** — 1-yarim vs 2-yarim + charchash tahlili
- **HeatmapsSection** — 3 ta heatmap (jamoa, top o'yinchi, zona)
- **InsightsSection** — Claude AI avtomatik xulosalari
- **ClaudePromptBar** — Foydalanuvchi savol berishi mumkin
- **AnalysisProgress** — Tahlil davom etayotganda real-time progress

### Video Upload page (`/matches/:id/upload`)

3 bosqichli wizard:

1. **Upload** — drag-drop yoki file picker (500 MB gacha, 5-10 daqiqalik klip)
2. **Calibrate** — 4 ta nuqta bosish (yuqori chap → yuqori o'ng → pastki o'ng → pastki chap)
3. **Confirm** — boshlashni tasdiqlash

Auto-skip ham bor — kalibrlash o'tkazib yuborilsa, ML servis avtomatik harakat qiladi.

## Backend integratsiya

Frontend `unwrap()` helper bilan Spring Boot'ning `{ ok, message, data }` envelope'ini parse qiladi.

JWT token `localStorage` ga `auth_token` kalit bilan saqlanadi.

### Real-time polling

`MatchDetailPage` ishlayotgan job'ni har 3 soniyada qayta so'raydi:

```javascript
useEffect(() => {
  if (!isAnalyzing) return;
  const interval = setInterval(() => refetchJobs(), 3000);
  return () => clearInterval(interval);
}, [isAnalyzing]);
```

### Heatmap rasmlari

ML servis MinIO'ga heatmap rasmini yuklaydi va key'ni qaytaradi:
```
result.heatmap_paths.team_a_heatmap = "match-123/heatmap-team-a.png"
```

Frontend `storageApi.getImageUrl(key)` orqali to'liq URLga aylantiradi:
```javascript
<img src={storageApi.getImageUrl(result.heatmap_paths.team_a_heatmap)} />
```

Spring Boot'da `GET /api/storage/image?key=...` endpoint bo'lishi kerak — pre-signed MinIO URL qaytarish yoki proxy qilish.

### Spring Boot fallback

Agar backend rasm bermasa, frontend **synthetic SVG heatmap** chizadi:
- 4-3-3 formation pozitsiyalari
- Multi-color gradient density blob'lari
- Pitch lines

## Color palette

```javascript
ink: { 950: '#08080b', 900: '#0a0a0d', 800: '#0e0e12', 700: '#1a1a22', ... }
lime.electric: '#c5ff50'  // Asosiy accent
coral: '#ff7a6b'          // Warning, fatigue
amber: '#ffb155'          // Mid-warmth
sky.electric: '#6bb4ff'   // Mehmon jamoa
```

## Mobile

Hozirgi versiya **desktop-first** (1280px+).

PWA bilan keyinroq mobile responsive qo'shilishi mumkin.

## Production checklist

- [ ] `.env.production` yarating va `VITE_API_URL` ni to'g'ri qo'ying
- [ ] `npm run build` chiqarib, `dist/` ni serverga ko'chiring
- [ ] nginx config yozing (yuqorida)
- [ ] SSL sertifikat o'rnating (Let's Encrypt)
- [ ] Backend CORS ruxsat berilganligini tekshiring (`application.yml` da `cors.allow-origins`)
- [ ] MinIO public bucket yoki pre-signed URL ishlayotganini tekshiring

## Muallif

Imaan Tech FC uchun ishlab chiqildi.
Spring Boot backend va RunPod ML service bilan birgalikda ishlaydi.

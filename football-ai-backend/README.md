# Football AI Analysis Platform

**Professional darajadagi futbol video tahlil platformasi** - LaLiga/Bundesliga klublari ishlatadigan texnologiyalar bilan.

## Asosiy imkoniyatlar

✅ **Video yuklash** (TV translyatsiya, MP4/AVI/MKV, 3 GB gacha — 90 daqiqalik full match)
✅ **O'yinchi tracking** (ByteTrack - har o'yinchiga ID)
✅ **Maydon kalibrlash** (Homography - pixel→metr)
✅ **Team classification** (jersey rangi orqali jamoa aniqlash)
✅ **Formation detection** (4-3-3, 4-2-3-1, va h.k. - 9 ta formatsiya)
✅ **Heatmap** (har o'yinchi va jamoa uchun)
✅ **Event detection** (gollar OCR + scoreboard tracking)
✅ **🆕 90 daqiqalik full match support** (streaming pipeline, RAM crash bo'lmaydi)
✅ **🆕 Match phases** (1-yarim / tanaffus / 2-yarim avtomatik aniqlanadi)
✅ **🆕 Timeline analytics** (har 5 daqiqada possession, sprint, tezlik snapshot)
✅ **🆕 Period comparison** (1-yarim vs 2-yarim avtomatik insights, Uzbek tilida)
✅ **🆕 Stamina analysis** (70-90 daqiqada charchash signali)
✅ **🆕 Checkpoint/Resume** (server crash bo'lsa - oxirgi joydan davom)
✅ **Claude AI taktik tahlil** (15+ kategoriya bo'yicha)
✅ **PDF hisobot** (chiroyli, professional)

## Texnik arxitektura (3 servis)

```
React UI ──> Spring Boot ──> PostgreSQL
                │
                ├──> MinIO (videolar, heatmaplar)
                ├──> Claude API (taktik tahlil)
                └──> Python ML Service (GPU):
                     ├─> YOLOv8 (player detection)
                     ├─> ByteTrack (tracking + ID)
                     ├─> OpenCV (Homography kalibrlash)
                     ├─> KMeans (team classification)
                     ├─> Custom (formation detection)
                     ├─> mplsoccer (heatmap chizish)
                     └─> EasyOCR (hisob tablosi o'qish)
```

## To'liq oqim (qanday ishlaydi)

### 1. Setup
- Klub ro'yxatdan o'tadi
- Jamoa, o'yinchi, raqib qo'shadi

### 2. Video yuklash
```
POST /api/matches/{id}/video  (multipart/form-data)
→ MinIO'da saqlanadi
```

### 3. Calibration (juda muhim!)
Murabbiy UI'da videoning birinchi frame'idan **maydonning 4-6 ta kalit nuqtasi**ni bosadi:
- Maydon burchaklari
- Jarima maydonchasi burchaklari
- Markaz aylanasi

```
POST /api/matches/{id}/analysis/start-with-calibration
{
  "top_left": [120, 80],
  "top_right": [1800, 80],
  "bottom_left": [50, 1000],
  "bottom_right": [1870, 1000]
}
```

Bu — **eng muhim qadam**. Pixeldan metrga aniq o'tkazish uchun homography matrix hisoblanadi.

### 4. ML pipeline (avtomatik, 5-15 daqiqa)
Python servis quyidagilarni qiladi:
1. Video → frame'lar (FFmpeg, 5 fps)
2. Har frame'da YOLOv8 → o'yinchilarni topish
3. ByteTrack → ularga ID berib kuzatish
4. Homography → pixel pozitsiyalarni metrga
5. Jersey ranglarini KMeans → jamoa klasterlash
6. O'rta pozitsiyalar → formatsiya tanish
7. Pozitsiyalar → heatmap chizish
8. Hisob tablosi OCR → gollar avtomatik

### 5. Taktik tahlil (Claude AI)
Hamma ma'lumotlar Claude'ga yuboriladi:
- Raqib formatsiyasi: "4-2-3-1"
- Bizning o'rtacha pozitsiyalar
- Heatmap zonalari
- Hisob va eventlar

Claude qaytaradi:
- Raqib kuchli/zaif tomonlari
- Hujum/himoya rejasi
- Birinchi 15 daqiqa rejasi
- Pressing zonalari
- Va h.k. (15+ kategoriya)

### 6. PDF hisobot
iText 8 bilan chiroyli PDF (heatmap rasmlar + Claude xulosasi).

## Cheklovlar (halol bayonot)

### TV translyatsiya bilan ishlaganda:

✅ **Yaxshi ishlaydi (75-85% aniqlik):**
- Player tracking (kameraga tushgan vaqtlarda)
- Formation detection (avg pozitsiyalar orqali)
- Team heatmap (umumiy)
- Goals (OCR orqali)
- Hisob va daqiqa

⚠️ **O'rtacha (50-70%):**
- Individual player heatmap (ko'p frame yo'qoladi)
- Press zones (faqat ko'rinadigan qism)
- Kartochkalar

❌ **Kerak emas yoki imkonsiz:**
- 100% aniq passing networks (har pas ko'rinmaydi)
- xG hisoblash (alohida model kerak)
- Off-ball runs (kameradan tashqari)

### Calibration nuqtalarning aniqligi:
- 4 nuqta = qo'pol heatmap (60%)
- 6-8 nuqta = aniq heatmap (75-85%)
- Avtomatik = 50-60% (production-da yaxshilanadi)

## Texnologiyalar

### Backend (Java)
- Spring Boot 3.3, Java 21
- PostgreSQL 16, JPA/Hibernate
- Spring Security + JWT
- MinIO (S3-compatible)
- iText 8 (PDF)
- OkHttp (Claude + ML servis bilan)

### ML Service (Python)
- FastAPI + Uvicorn
- PyTorch + Ultralytics YOLOv8 (GPU)
- supervision (ByteTrack)
- OpenCV (homography)
- scikit-learn (KMeans)
- mplsoccer (futbol heatmap)
- EasyOCR
- PySceneDetect

### AI
- Anthropic Claude (Sonnet 4)

## Ishga tushirish

### Talablar
- Docker + Docker Compose
- NVIDIA GPU + Container Toolkit (RTX 3060+)
- Claude API key

### Quick start

```bash
# .env yarating
cat > .env << EOF
CLAUDE_API_KEY=sk-ant-your-key
DB_PASSWORD=secure-password
JWT_SECRET=$(openssl rand -hex 32)
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=$(openssl rand -hex 16)
ML_INTERNAL_TOKEN=$(openssl rand -hex 16)
EOF

# Hammasini ishga tushirish
docker-compose up -d

# Tayyor:
# - Backend: http://localhost:8080/api/swagger-ui.html
# - ML Service: http://localhost:8000/docs
# - MinIO Console: http://localhost:9001
```

### GPU tekshiruvi

```bash
# Hostda
nvidia-smi

# Docker'da
docker exec football-ai-ml nvidia-smi

# ML servis health
curl http://localhost:8000/health
# javob: {"status": "ok", "gpu_available": true, "gpu_name": "NVIDIA GeForce RTX 3060", ...}
```

## Loyiha tuzilishi

```
football-ai/
├── docker-compose.yml         # Hamma servislarni boshqarish (4 servis + GPU)
├── Dockerfile                 # Spring Boot
├── pom.xml
├── README.md
│
├── src/main/java/uz/footballai/   # Java backend
│   ├── auth/, user/, club/, team/, player/, opponent/, match/
│   ├── ai/                     # Claude integratsiyasi
│   ├── video/                  # Video upload + ML servis client
│   │   ├── VideoAnalysisService.java
│   │   ├── MlServiceClient.java
│   │   └── VideoAnalysisController.java
│   ├── report/                 # PDF generatsiyasi
│   ├── config/
│   └── common/
│
└── ml-service/                 # Python ML mikroservisi
    ├── Dockerfile              # CUDA + PyTorch
    ├── requirements.txt
    └── app/
        ├── main.py             # FastAPI entry
        ├── core/               # config, logging
        ├── schemas/            # Pydantic models
        ├── models/             # YOLO + OCR
        ├── tracking/           # ByteTrack player tracker, team classifier
        ├── calibration/        # Field homography
        ├── tactical/           # Formation detection, heatmap
        └── services/           # Pipeline, frame extractor, scene detector
```

## API endpointlar

### Foydalanuvchi (Backend)
| Method | URL | Tavsif |
|--------|-----|--------|
| POST | `/api/auth/register` | Klub ro'yxatdan o'tkazish |
| POST | `/api/teams` | Jamoa yaratish |
| POST | `/api/opponents` | Raqib qo'shish |
| POST | `/api/matches` | O'yin yaratish |
| POST | `/api/matches/{id}/video` | Video yuklash (3 GB gacha) |
| POST | `/api/matches/{id}/analysis/start` | Auto kalibrlash bilan tahlil |
| POST | `/api/matches/{id}/analysis/start-with-calibration` | Qo'lda kalibrlash bilan |
| POST | `/api/matches/{id}/analysis/start-long-form` | **🆕 90 daqiqalik to'liq match (timeline + period + stamina)** |
| POST | `/api/matches/{id}/analysis/jobs/{jobId}/resume` | **🆕 Crash bo'lsa qaytadan boshlash** |
| POST | `/api/matches/{id}/analysis/jobs/{jobId}/cancel` | **🆕 Davom etayotgan jobni bekor qilish** |
| GET | `/api/matches/{id}/analysis/jobs/{jobId}` | Job statusi (real-time progress, daqiqa, ETA) |
| GET | `/api/matches/{id}/analysis/jobs/{jobId}/result` | **🆕 Tugagan tahlilning raw JSON** (timeline, periods, stamina, heatmap) |
| POST | `/api/analysis/request` | Claude taktik tahlil |
| POST | `/api/reports/{id}/generate-pdf` | PDF yaratish |

### ML Service (internal)
| Method | URL | Tavsif |
|--------|-----|--------|
| GET | `/health` | Health + GPU |
| POST | `/analyze` | Video tahlilni boshlash (is_long_form qo'llab-quvvatlanadi) |
| POST | `/analyze/resume/{id}` | **🆕 Checkpoint'dan davom etish** |
| GET | `/jobs/{id}` | Job statusi (current_minute, current_phase, eta_seconds) |
| GET | `/jobs/{id}/result` | Tugagan natija (timeline, periods, stamina) |
| DELETE | `/jobs/{id}` | **🆕 Jobni bekor qilish** |

## 🆕 90 daqiqalik video qo'llanma

### Tahlil davomiyligi (RTX 3090 da)
- 30-sek video: ~30 sekund tahlil
- 5 daqiqalik video: ~1.5 daqiqa
- 45 daqiqalik 1-yarim: ~12-15 daqiqa
- **90 daqiqalik to'liq match: ~25-35 daqiqa**

### Resurslar (90 min, 1080p)
- **RAM**: ~3-4 GB peak (streaming - disk'ga frame yozilmaydi)
- **VRAM**: ~6-8 GB (RTX 3060+ tavsiya)
- **Disk**: ~3 GB video + ~50 MB checkpoint + ~200 MB heatmaps
- **MinIO**: video saqlash + heatmaplar (`football-analytics` bucket)

### Yangi maydonlar (long-form natijalarda)

```json
{
  "is_long_form": true,
  "match_phases": [
    {"phase_type": "FIRST_HALF", "start_seconds": 0, "end_seconds": 2780, "confidence": 0.85},
    {"phase_type": "HALFTIME", "start_seconds": 2780, "end_seconds": 3680, "confidence": 0.75},
    {"phase_type": "SECOND_HALF", "start_seconds": 3680, "end_seconds": 5550, "confidence": 0.85}
  ],
  "timeline": [
    {
      "window_start_min": 0, "window_end_min": 5, "phase": "FIRST_HALF",
      "home_possession_pct": 58.2, "away_possession_pct": 41.8,
      "home_avg_speed_kmh": 12.4, "home_sprint_count": 8,
      "home_total_distance_m": 4250.5, "home_attacking_third_pct": 35.1
    }
  ],
  "period_comparison": {
    "first_half": { "home_possession_pct": 55, "home_sprint_count": 67 },
    "second_half": { "home_possession_pct": 62, "home_sprint_count": 51 },
    "home_stamina_drop_pct": 24.0,
    "momentum_shift": "HOME_GAINED",
    "insights": [
      "2-yarimda Uy jamoasi o'yinni boshqarish foizi 55% → 62% ga oshdi",
      "Mehmon jamoasida charchash signali: 2-yarimda sprint 31% kamaydi"
    ]
  },
  "stamina_analysis": {
    "sprint_buckets_home": [22, 23, 22, 19, 17, 15],
    "sprint_buckets_away": [20, 21, 18, 14, 11, 9],
    "away_fatigue_detected": true,
    "fatigue_minute_away": 75
  }
}
```

## Calibration UI uchun maslahat

Frontend'da murabbiy quyidagi tartibda nuqtalarni bosadi:

1. **Yuqori chap burchak** (`top_left`) - maydon burchagi
2. **Yuqori o'ng burchak** (`top_right`)
3. **Pastki chap burchak** (`bottom_left`)
4. **Pastki o'ng burchak** (`bottom_right`)
5. (ixtiyoriy) **Markaz** (`center`)
6. (ixtiyoriy) **Markaz tepa** (`center_top`) - markaz aylanasi yuqori nuqtasi
7. (ixtiyoriy) **Markaz pastki** (`center_bottom`)
8. (ixtiyoriy) Jarima maydonchalari burchaklari

Qancha ko'p nuqta - shuncha aniq homography.

## Tarif rejalar

| Tarif | Jamoalar | Tahlillar/oy | Video tahlil | Aniqlik |
|-------|----------|--------------|--------------|---------|
| FREE | 1 | 3 | ❌ | - |
| BASIC | 3 | 30 | 5 video/oy | Avto cal (60%) |
| PRO | Cheksiz | Cheksiz | Cheksiz | Manual cal (75-85%) |
| CLUB+ | Shartnoma | Shartnoma | + xizmat | Custom kamera support |

## Keyingi bosqichlar

1. **Football-specific YOLO** - SoccerNet datasetda fine-tune (90%+ aniqlik)
2. **Jersey number recognition** - alohida CRNN model (o'yinchi raqamlari)
3. **Action recognition** - pas/shot/dribble (PySLowFast yoki MMAction)
4. **TrackNet** - to'pni aniq kuzatish
5. **Auto field calibration** - PnLCalib yoki KpSFR (avtomatik = manual sifati)
6. **Real-time analysis** - jonli efir paytida
7. **React frontend** - calibration UI bilan
8. **Mobil ilova**

## Resurslar

- [SoccerNet](https://www.soccer-net.org/) - dataset va modellar
- [TrackNet](https://github.com/qaz812345/TrackNet) - to'p tracking
- [Roboflow Sports](https://github.com/roboflow/sports) - tayyor futbol kodlari
- [supervision](https://supervision.roboflow.com/) - tracking utilities
- [mplsoccer](https://mplsoccer.readthedocs.io/) - futbol vizualizatsiya

## Litsenziya

Proprietary - Abdulloh & Imaan Tech

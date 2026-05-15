# Football Analysis — Enhanced v2

Abdullah Tarek'ning [football_analysis](https://github.com/abdullahtarek/football_analysis)
loyihasi asosida professional darajada qayta ishlangan versiya. RunPod GPU
server'da PyCharm orqali ishlash uchun moslangan, bir nechta kritik bug'lar
tuzatilgan va yangi avtomatik tahlil moduli qo'shilgan.

## Asosiy yaxshilanishlar (v2)

| Komponent | v1 (original) | v2 (enhanced) |
|---|---|---|
| **View transformer** | Hardcoded 08fd33_4.mp4 uchun | JSON-asosli kalibrlash, har video uchun moslashadi |
| **Speed estimator** | ZeroDivisionError, noise spikes | Smoothing + outlier filter, 40 km/h cap |
| **Team assigner** | KMeans n_init=1, hardcoded `if id==91` | n_init=10, manual override olib tashlandi |
| **Tracker** | Default ByteTrack params | Tighter params (track_thresh=0.5, buffer=60) |
| **Track filter** | yo'q | **YANGI** — ID switching artefaktlarini tozalash (50 ID → 13) |
| **Team direction** | yo'q | **YANGI** — qaysi kamanda qaysi tomonga hujum, avtomatik aniqlash |
| **Sprint thresholds** | hardcoded 21 km/h | **Adaptive** — pro/youtube/amateur preset'lari |
| **Output** | Faqat video | **JSON + interaktiv HTML dashboard + heatmap PNG'lar + pass network'lar + video overlay** |

## Tezkor boshlash (RunPod)

```bash
# 1. Tizim paketlari
apt-get update && apt-get install -y libgl1 libglib2.0-0 ffmpeg unzip wget

# 2. Python paketlari (PyTorch RunPod template'da bor - qaytadan o'rnatmang!)
pip install --break-system-packages \
    ultralytics supervision opencv-python \
    pandas matplotlib scikit-learn gdown yt-dlp

# 3. Loyiha
cd /workspace
unzip football_analysis-main.zip
cd football_analysis-main

# 4. Model va asl test video
gdown 1DC2kCygbBWUKheQ_9cFziCsYVSRw6axK -O models/best.pt
gdown 1t6agoqggZKx6thamUuPAIdN_1zR9v9S_ -O input_videos/08fd33_4.mp4

# 5. Sinov - asl video bilan
python main.py
```

Asl video bilan ishlasa, dashboard'da real natijalarni ko'rasiz.

## Yangi videoda ishlash (3 qadam)

### 1-qadam: Videoni tayyorlash

YouTube'dan kesib olish:
```bash
yt-dlp \
    -f "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080]" \
    --download-sections "*7:55-8:25" \
    --merge-output-format mp4 \
    -o "input_videos/match.mp4" \
    "https://youtu.be/T2TAHYKo3UU"
```

**MUHIM**: Videoning RAM hajmiga e'tibor bering:
- 1080p 30 sek ≈ 5 GB RAM
- 1080p 1 daqiqa ≈ 10 GB RAM
- 1080p 5 daqiqa ≈ 47 GB ❌ (OOM)

Sinash uchun avval 30 sek bilan boshlang.

### 2-qadam: View transformer kalibrlash (eng muhim!)

Bu qadam **majburiy** — bir video uchun kalibrlangan ViewTransformer
boshqa videoda noto'g'ri ishlaydi (tezlik 100+ km/h chiqishi mumkin).

#### A) Freymni rasm sifatida saqlang

```bash
python tools/calibration_helper.py extract --video input_videos/match.mp4
```

`calibration_frame.png` yaratiladi.

#### B) Rasmda 4 burchakni toping

Yaratilgan `calibration_frame.png` ni oching va maydondagi **bilingan
o'lchamli to'rtburchak**ni topib, uning 4 burchagining piksel
koordinatasini yozib oling.

**Eng oson tanlov: yarim maydon** (52.5 × 68 m):
- p1 = pastki-chap (orqa chiziq + chap yon chiziq kesishuvi)
- p2 = yuqori-chap (markaziy chiziq + chap yon chiziq)
- p3 = yuqori-o'ng (markaziy chiziq + o'ng yon chiziq)
- p4 = pastki-o'ng (orqa chiziq + o'ng yon chiziq)

**Koordinatalarni qanday topish:**
- **PyCharm Pro Image Viewer**: rasmni oching, mouse harakat ettirsangiz statusbar'da koordinata ko'rinadi
- **GIMP**: Tools → Measure
- **Onlayn**: https://imagemapper.online (drag-and-drop, koordinatalar avtomatik chiqadi)
- **Paint (Windows)**: cursor pozitsiyasi pastdagi statusbar'da

#### C) Kalibrlashni saqlash

```bash
python tools/calibration_helper.py save \
    --p1 "X1,Y1" --p2 "X2,Y2" --p3 "X3,Y3" --p4 "X4,Y4" \
    --preset half_pitch
```

Misol (chinakam koordinatalar bilan):
```bash
python tools/calibration_helper.py save \
    --p1 "150,950" --p2 "300,250" --p3 "1620,250" --p4 "1770,950" \
    --preset half_pitch
```

#### D) Kalibrlashni tekshirish (vizual)

```bash
python tools/calibration_helper.py check
```

`calibration_check.png` yaratiladi. Ochib qarang — yashil to'rtburchak
maydonning yarmiga (yoki tanlangan zonaga) **aniq mos kelishi kerak**.

Preset variantlari:
- `full_pitch` — 105 × 68 m (butun maydon, agar 4 burchak ko'rinsa)
- `half_pitch` — 52.5 × 68 m (yarim maydon, **eng tavsiya**)
- `penalty_box` — 40.32 × 16.5 m (jarima maydonchasi)
- `center_circle` — 18.3 × 18.3 m (markaziy doira atrofi)
- `custom` — `--world-length` va `--world-width` argumentlari bilan

### 3-qadam: Pipeline'ni ishga tushirish

```bash
# Eski stub fayllarini tozalang (yangi video uchun)
rm -f stubs/*.pkl

# Ishga tushirish
python main.py --video input_videos/match.mp4 --preset youtube
```

Preset'lar:
- `pro` — professional broadcast (yuqori sifat, sprint=21 km/h)
- `youtube` — YouTube'dan kesilgan match (default, sprint=18 km/h)
- `amateur` — amator video (looser, sprint=15 km/h)

## Pipeline natijalari

```
output_videos/
├── output_video.avi              # video + analytics overlay panel
└── analytics/
    ├── analytics.json            # barcha metrikalar (boshqa toollar uchun)
    ├── dashboard.html            # interaktiv dark-theme dashboard
    ├── heatmap_team_1.png        # kamanda heatmap'lari
    ├── heatmap_team_2.png
    ├── heatmap_player_*.png      # top 5 eng faol oyinchi
    ├── pass_network_team_1.png   # pass tarmog'i diagrammasi
    └── pass_network_team_2.png
```

`dashboard.html` ni RunPod'dan yuklab brauzerda oching — to'liq
self-contained (rasmlar base64 embedded).

## Hisoblanadigan metrikalar

### Possession
- foiz, davomiyligi (jami va o'rtacha)
- defensiv/o'rta/hujum uchligida vaqt taqsimoti
- hujum uchligidagi possession sek

### Speed & Distance
- max va o'rtacha km/h
- jami masofa (m)
- 4 ta intensivlik bandida foiz (walking/jogging/running/sprinting)
- har oyinchi sprint soni
- top sprinter

### Heatmap
- 20×20 grid pozitsiya zichligi
- centroid (markaz nuqta) va spread (kompaktlik)
- **avtomatik team direction normalization** — ikki kamanda heatmap'i
  to'g'ri tomonda chiqaradi
- top-5 oyinchi alohida heatmap

### Pass Network
- jami passes va aniqlik %
- top passer, top receiver
- pass tarmog'i grafi (kim kimga uzatdi)
- turnovers (yo'qotilgan/qaytib olingan)

### Pressing
- to'p egasi va eng yaqin himoyachi orasida o'rtacha masofa
- high press % (5m dan yaqin masofa)
- pressing zonasi (low_block/mid_press/high_press)

### Avtomatik xulosa (TeamComparator)
Barcha metrikalarni solishtirib, har kamandaning kuchli/zaif tomonlarini
avtomatik aniqlaydi va Uzbek tilida narrative yaratadi.

## Track Filter (yangi feature)

ByteTrack ID switching natijasida hosil bo'ladigan "fake" tracklarni
filtrlaydi. 3 ta mezon (default):

1. **Lifetime ≥ 1 sek** (24 freym) — qisqa muddatli IDlarni o'chirish
2. **Distance ≥ 1m** — joyida tursa ham harakatlanmagan IDlarni o'chirish
3. **Appearance ≥ 5%** — umumiy freymning kamida 5% da ko'ringan

Natija: 50+ ID → ~13 ID (haqiqiy oyinchilar soni).

Buni o'zgartirish:
```python
# team_analytics/config.py ichida AnalyticsConfig
min_track_lifetime_frames=12   # qisqaroq tracklar ham qoladi
min_track_distance_m=0.5
```

## Team Direction Detector (yangi feature)

Birinchi 5 sek ma'lumotlardan har kamandaning markaz pozitsiyasini topib,
qaysi kamanda qaysi tomonga hujum qilishini aniqlaydi. Bu heatmap'larda
ikki kamandani **bir tomonga** ko'rsatishni ta'minlaydi (taqqoslash uchun
qulayroq).

## Loyiha tuzilishi

```
football_analysis-main/
├── main.py                            # CLI args bilan
├── requirements.txt
├── README.md (bu fayl)
├── SETUP.md
│
├── view_transformer/
│   ├── view_transformer.py            # JSON-based, fallback default bilan
│   └── calibration.json               # Kalibrlash (ixtiyoriy)
│
├── speed_and_distance_estimator/
│   └── speed_and_distance_estimator.py # Smoothing + outlier filter
│
├── team_assigner/
│   └── team_assigner.py               # n_init=10, hardcoded id olib tashlandi
│
├── trackers/
│   └── tracker.py                     # Tighter ByteTrack params
│
├── team_analytics/                    # YANGI MODUL
│   ├── __init__.py
│   ├── config.py                      # Tunable parametrlar
│   ├── analyzer.py                    # Orchestrator
│   ├── data_extractor.py              # tracks -> DataFrame
│   ├── track_filter.py                # ID switching artefaktlari
│   ├── team_direction.py              # Hujum yo'nalishi
│   ├── possession.py
│   ├── speed_profile.py
│   ├── heatmap.py
│   ├── pass_network.py
│   ├── pressing.py
│   ├── comparator.py                  # Kuchli/zaif tomon
│   ├── reporter.py                    # JSON + HTML
│   └── video_overlay.py               # Video panel
│
├── tools/                             # Yordamchi skriptlar
│   ├── calibration_helper.py          # YANGI - oddiy kalibrlash
│   ├── calibrate_view_interactive.py  # Eski matplotlib variant
│   ├── trim_video.py
│   └── yolo_test.py
│
├── camera_movement_estimator/         # Optical flow
├── player_ball_assigner/              # To'p egaligi
├── utils/                             # Video o'qish/saqlash
├── input_videos/
├── models/
├── output_videos/
└── stubs/                             # Tracking cache
```

## Ko'p uchraydigan xatolar

### `IndexError: list index out of range` (tracker.py:191)
Stub fayllar boshqa video uchun keshlangan:
```bash
rm stubs/*.pkl
```

### `ZeroDivisionError: float division by zero`
Bu original kodning bug'i — agar yangi videoda ham chiqsa:
```bash
python3 -c "
path = 'speed_and_distance_estimator/speed_and_distance_estimator.py'
with open(path) as f: c = f.read()
old = 'speed_meteres_per_second = distance_covered/time_elapsed'
new = 'speed_meteres_per_second = distance_covered/time_elapsed if time_elapsed > 0 else 0'
if old in c: open(path, 'w').write(c.replace(old, new)); print('OK')
"
```

### Tezlik 100+ km/h yoki 0 km/h
View transformer kalibrlanmagan. Yuqoridagi 2-qadamni bajaring.

### "40-50 ta unique player ID"
ByteTrack ID switching. Track filter avtomatik tozalaydi, lekin parametrlarni
sozlash kerak bo'lishi mumkin (`team_analytics/config.py`).

### `Out of memory`
Video juda uzun. `tools/trim_video.py` orqali kesing.

## Litsenziya

Asl loyiha — Abdullah Tarek (MIT). Bu kengaytma — uning ustida qurilgan,
o'zgartirishlar erkin foydalanish uchun.

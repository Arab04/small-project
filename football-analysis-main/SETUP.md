# Football Analysis — Ishga tushirish bo'yicha qo'llanma

## 1. Python muhit (3.10 yoki 3.11 tavsiya qilinadi)

```bash
# Repoga kiring
cd football_analysis-main

# Virtual environment yarating
python3 -m venv venv

# Aktivatsiya
# Linux/Mac:
source venv/bin/activate
# Windows (PowerShell):
# .\venv\Scripts\Activate.ps1

# Kutubxonalarni o'rnating
pip install -r requirements.txt
```

> **Eslatma:** `ultralytics` o'rnatilganda PyTorch ham tushadi (~2 GB).
> GPU bor bo'lsa CUDA versiyasini alohida o'rnatish tavsiya etiladi.

## 2. Kerakli fayllarni yuklab olish

```bash
# 1) YOLO model
# https://drive.google.com/file/d/1DC2kCygbBWUKheQ_9cFziCsYVSRw6axK/view
# -> models/best.pt deb saqlang

# 2) Sample video
# https://drive.google.com/file/d/1t6agoqggZKx6thamUuPAIdN_1zR9v9S_/view
# -> input_videos/08fd33_4.mp4 deb saqlang
```

CLI orqali yuklab olish (`gdown`):
```bash
pip install gdown
gdown 1DC2kCygbBWUKheQ_9cFziCsYVSRw6axK -O models/best.pt
gdown 1t6agoqggZKx6thamUuPAIdN_1zR9v9S_ -O input_videos/08fd33_4.mp4
```

## 3. Ishga tushirish

```bash
python main.py
```

Natija: `output_videos/output_video.avi` fayli paydo bo'ladi.

## 4. Muhim eslatma — stub fayllar haqida

`stubs/` papkasidagi `.pkl` fayllar **aynan `08fd33_4.mp4` videosi uchun** keshlangan
tracking natijalari. Boshqa video ishlatmoqchi bo'lsangiz, `main.py` da:

```python
# read_from_stub=True ni False ga o'zgartiring
tracks = tracker.get_object_tracks(video_frames,
                                    read_from_stub=False,   # <-- shu yer
                                    stub_path='stubs/track_stubs.pkl')
```

Aks holda eski natijalar yangi videoga noto'g'ri qo'llanadi.

## 5. Birinchi run sekin bo'ladi

YOLO modelini yuklash va birinchi inference taxminan 1-3 daqiqa
oladi (CPU'da ko'proq). GPU bor bo'lsa real-time'ga yaqin tezlikda ishlaydi.

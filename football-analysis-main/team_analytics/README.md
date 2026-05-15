# team_analytics

Avtomatik kamanda tahlili paketi. `tracks` obyektidan barcha metrikalarni
hisoblab, JSON / HTML / video overlay shaklida chiqaradi.

## Tezkor foydalanish

```python
from team_analytics import MatchAnalyzer, AnalyticsOverlay

analyzer = MatchAnalyzer(
    fps=24,
    court_width=68.0,    # maydon uzunligi (y-axis)
    court_length=23.32,  # kalibrlangan to'rtburchak kengligi (x-axis)
    output_dir='output_videos/analytics',
)

results = analyzer.run(tracks, team_ball_control)
analyzer.write_reports(results)  # JSON + HTML

# Video freymlariga panel chizish
overlay = AnalyticsOverlay()
out_frames = overlay.draw(out_frames, results)
```

## Modullar va metrikalar

| Modul | Klass | Hisoblanadigan |
|---|---|---|
| `data_extractor.py` | `DataExtractor` | `tracks` dict → `players_df`, `ball_df`, `metadata` |
| `possession.py` | `PossessionAnalyzer` | possession %, davomiyligi, zonalar (def/mid/att), hujum 1/3 vaqt |
| `speed_profile.py` | `SpeedProfileAnalyzer` | max/avg km/h, sprint soni, intensivlik bandlari, top sprinter |
| `heatmap.py` | `HeatmapGenerator` | 20×20 grid zichlik, centroid, spread (kompaktlik), PNG export |
| `pass_network.py` | `PassNetworkAnalyzer` | pass count, accuracy %, top passer/receiver, tarmoq diagrammasi |
| `pressing.py` | `PressingAnalyzer` | o'rt/median pressure distance, high press %, press zonasi |
| `comparator.py` | `TeamComparator` | strengths/weaknesses ro'yxati + Uzbek narrative |
| `reporter.py` | `Reporter` | JSON + dark-themed HTML dashboard (heatmap'lar embedded) |
| `video_overlay.py` | `AnalyticsOverlay` | Yon panel chizish (har freymga) |
| `analyzer.py` | `MatchAnalyzer` | Hammasini bir joyda chaqiruvchi orchestrator |

## Konventsiyalar

**Maydon o'qlari** (asl `view_transformer.py` bilan mos):
- **`x` (court_length=23.32m)** — yon-yon (chap/o'ng). Faqat kalibrlangan to'rtburchak kengligi.
- **`y` (court_width=68m)** — maydon bo'ylab (himoya/hujum). Asosiy tahlil shu o'q bo'yicha.
- Hujum yo'nalishi: `y` ortib boradi.

**Maydon uchligi** (y-axis bo'yicha):
- `defensive`: `y < 22.7m`
- `middle`: `22.7m ≤ y < 45.3m`
- `attacking`: `y ≥ 45.3m`

**Tezlik bandlari** (FIFA standartiga yaqin):
- walking: `< 7 km/h`
- jogging: `7-14 km/h`
- running: `14-21 km/h`
- sprinting: `> 21 km/h`

**Sprint ta'rifi**: `> 21 km/h` davomida `≥ 1 sek` (24 freym).

## Threshold'larni o'zgartirish

`comparator.py` ichida `THRESHOLDS` dict mavjud. Sizning videongizning
sifati va davomiyligiga qarab tweak qilishingiz mumkin:

```python
from team_analytics.comparator import TeamComparator
TeamComparator.THRESHOLDS['sprint_advantage'] = 3  # default 5
TeamComparator.THRESHOLDS['possession_dominant'] = 55  # default 60
```

Yoki `SpeedProfileAnalyzer` constructor'ida sprint chegarasini o'zgartiring
(amator video uchun 21 km/h juda yuqori bo'lishi mumkin):

```python
SpeedProfileAnalyzer(
    sprint_threshold_kmh=18.0,
    sprint_min_duration_s=0.7,
).analyze(...)
```

## Output

`output_dir` ichida quyidagi fayllar yaratiladi:

```
output_videos/analytics/
├── analytics.json                    # Strukturalangan barcha metrikalar
├── dashboard.html                    # Self-contained interaktiv hisobot
├── heatmap_team_1.png                # Kamanda heatmap'lari
├── heatmap_team_2.png
├── heatmap_player_<ID>_team<N>.png   # Top 5 eng faol oyinchi
├── pass_network_team_1.png           # Pass tarmog'i diagrammasi
└── pass_network_team_2.png
```

## Kengaytirish

Yangi metrika qo'shish:

1. Yangi fayl yarating, masalan `team_analytics/shooting.py`:
   ```python
   class ShootingAnalyzer:
       def analyze(self, players_df, ball_df, metadata):
           return {'team_1': {...}, 'team_2': {...}}
   ```

2. `analyzer.py` ichida `run()` metodiga qo'shing:
   ```python
   from .shooting import ShootingAnalyzer
   ...
   results['shooting'] = ShootingAnalyzer().analyze(players_df, ball_df, metadata)
   ```

3. (Ixtiyoriy) `reporter.py` ichida `_team_card()` metodiga ko'rsatish qatori qo'shing.
4. (Ixtiyoriy) `comparator.py` ichida solishtirish mantiqi yarating.

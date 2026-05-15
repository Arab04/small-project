"""
Analytics Configuration
=======================
Barcha tunable parametrlar bir joyda. Foydalanuvchi videongizning
sifatiga qarab buyerda o'zgartirish kiritishi mumkin.

Adaptive presets:
    pro       - professional broadcast (1080p, stabil kamera, yuqori sifat)
    amateur   - amator video (720p, sayoz tracking, kamroq detection)
    youtube   - YouTube'dan kesilgan match (o'rta sifat)

Foydalanish:
    from team_analytics.config import AnalyticsConfig, PRESETS
    cfg = PRESETS['amateur']
    # yoki maxsus:
    cfg = AnalyticsConfig(sprint_threshold_kmh=15.0, ...)
"""
from dataclasses import dataclass, field, asdict


@dataclass
class AnalyticsConfig:
    # === Track filter (eng muhim) ===
    # Qancha freym tirik bo'lgan tracklar saqlanadi (qisqacharog'i = ID switching artefakti)
    min_track_lifetime_frames: int = 24       # 1 sek (24 fps)
    # Qancha metrdan kam bosib o'tgan tracklar e'tiborga olinmaydi
    min_track_distance_m: float = 1.0
    # Faqat ko'p ko'rinish bergan tracklar (aniq oyinchi sifatida)
    min_track_appearance_pct: float = 5.0     # umumiy freymning 5% da kam bo'lmasdan ko'ringan

    # === Sprint detection ===
    # Pro futbolda 21 km/h, amator videoda 15-18 km/h yaxshi
    sprint_threshold_kmh: float = 18.0
    sprint_min_duration_s: float = 0.5

    # === Speed bands (intensivlik tasniflari) ===
    walking_max_kmh: float = 6.0
    jogging_max_kmh: float = 12.0
    running_max_kmh: float = 18.0
    # > running_max = sprinting

    # === Pass detection ===
    # Egalik o'zgarishida qancha vaqt o'tishi pass deb hisoblanadi
    pass_max_gap_seconds: float = 1.5
    # Pass deb hisoblash uchun minimal egalik davomiyligi
    possession_min_duration_s: float = 0.3

    # === Pressing ===
    high_press_distance_m: float = 5.0

    # === Comparator threshold'lari ===
    possession_dominant_pct: float = 60.0
    sprint_advantage_count: int = 5
    pressure_high_distance_m: float = 4.5
    pressure_low_distance_m: float = 8.0
    pass_acc_good_pct: float = 70.0     # 80% pro uchun, amatorda 70% yaxshi
    distance_advantage_m: float = 300.0

    # === Heatmap ===
    heatmap_grid_x: int = 20
    heatmap_grid_y: int = 20
    normalize_team_direction: bool = True   # ikki kamandani bir tomonga ag'darish

    def as_dict(self):
        return asdict(self)


# Tayyor preset'lar
PRESETS = {
    'pro': AnalyticsConfig(
        # Professional broadcast - default param'lar yaxshi ishlaydi
        sprint_threshold_kmh=21.0,
        sprint_min_duration_s=1.0,
        min_track_lifetime_frames=24,
        min_track_distance_m=2.0,
        pass_acc_good_pct=80.0,
        distance_advantage_m=500.0,
    ),
    'amateur': AnalyticsConfig(
        # Amator video - looser thresholds
        sprint_threshold_kmh=15.0,
        sprint_min_duration_s=0.4,
        min_track_lifetime_frames=12,
        min_track_distance_m=0.5,
        pass_acc_good_pct=60.0,
        possession_dominant_pct=55.0,
    ),
    'youtube': AnalyticsConfig(
        # YouTube'dan kesilgan match — qattiq filter, ~22 real ID
        sprint_threshold_kmh=18.0,
        sprint_min_duration_s=0.3,          # 0.3s — qisqa sprint'larni ham ushlash
        min_track_lifetime_frames=72,       # 3 sek (24 fps) — juda qisqa tracklar artefakt
        min_track_distance_m=5.0,           # 5m dan kam = harakatsiz yoki artefakt
        min_track_appearance_pct=15.0,      # kamida 15% ko'rinishi kerak (real o'yinchi)
        pass_acc_good_pct=70.0,
    ),
}


def auto_select_preset(metadata):
    """
    Heuristik: video uzunligi va FPS asosida preset tanlash.

    Pro broadcasts odatda 1080p+ va 25-50 fps. YouTube cliplari ham 1080p,
    lekin tracking qiyinroq bo'lishi mumkin (dynamic camera). Amator esa
    pastroq sifat.

    Foydalanuvchi har doim explicit preset belgilashi mumkin.
    """
    fps = metadata.get('fps', 24)
    duration = metadata.get('duration_s', 0)

    # Hozircha default 'youtube' - eng keng tarqalgan use case
    return 'youtube'

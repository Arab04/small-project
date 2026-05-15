"""
team_analytics
==============
Football match analytics package (v2 — track filter + team direction + adaptive config)

Asosiy entry point:
    from team_analytics import MatchAnalyzer
    from team_analytics import AnalyticsOverlay

Adaptive config:
    from team_analytics import PRESETS, AnalyticsConfig

    analyzer = MatchAnalyzer(preset='youtube')  # yoki 'pro', 'amateur'

Yoki maxsus config bilan:
    from team_analytics.config import AnalyticsConfig
    cfg = AnalyticsConfig(sprint_threshold_kmh=12.0, min_track_lifetime_frames=10)
    analyzer = MatchAnalyzer(config=cfg)
"""
from .analyzer import MatchAnalyzer
from .video_overlay import AnalyticsOverlay
from .config import AnalyticsConfig, PRESETS
from .tactical_analyzer import TacticalAnalyzer

__all__ = ['MatchAnalyzer', 'AnalyticsOverlay', 'AnalyticsConfig', 'PRESETS', 'TacticalAnalyzer']

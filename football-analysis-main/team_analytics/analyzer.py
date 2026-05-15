"""
Match Analyzer (orchestrator)
==============================
Barcha sub-analyzerlarni bir joyda chaqiradi.

Pipeline:
    1. DataExtractor: tracks -> DataFrame
    2. TrackFilter: ID switching artefaktlarini o'chirish (50 ID -> 13)
    3. TeamDirectionDetector: hujum yo'nalishini aniqlash
    4. (parallel) Possession, Speed, Heatmap, Pass, Pressing
    5. TeamComparator: kuchli/zaif tomonlarni aniqlash
    6. Reporter: JSON + HTML
"""
import os
import time

from .config import AnalyticsConfig, PRESETS, auto_select_preset
from .data_extractor import DataExtractor
from .track_filter import TrackFilter
from .team_direction import TeamDirectionDetector
from .possession import PossessionAnalyzer
from .speed_profile import SpeedProfileAnalyzer
from .heatmap import HeatmapGenerator
from .pass_network import PassNetworkAnalyzer
from .pressing import PressingAnalyzer
from .comparator import TeamComparator
from .reporter import Reporter


class MatchAnalyzer:
    def __init__(self, fps=24, court_width=68.0, court_length=23.32,
                 output_dir='output_videos/analytics', verbose=True,
                 config=None, preset=None):
        """
        Args:
            config: AnalyticsConfig instance, yoki None (preset ishlatiladi)
            preset: 'pro', 'amateur', 'youtube' yoki None (auto)
        """
        self.fps = fps
        self.court_width = court_width
        self.court_length = court_length
        self.output_dir = output_dir
        self.verbose = verbose

        # Config tanlash
        if config is not None:
            self.config = config
        elif preset is not None:
            if preset not in PRESETS:
                raise ValueError(f"Unknown preset '{preset}'. Available: {list(PRESETS.keys())}")
            self.config = PRESETS[preset]
        else:
            self.config = PRESETS['youtube']  # default

        os.makedirs(output_dir, exist_ok=True)

    def _log(self, msg):
        if self.verbose:
            print(f"[analytics] {msg}", flush=True)

    def run(self, tracks, team_ball_control=None):
        t0 = time.time()

        # 1. Data extraction
        self._log("Ma'lumot chiqarilmoqda...")
        extractor = DataExtractor(
            fps=self.fps,
            court_width=self.court_width,
            court_length=self.court_length,
        )
        players_df, ball_df, metadata = extractor.extract(tracks, team_ball_control)
        total_frames = metadata['total_frames']
        self._log(f"  Boshlang'ich: {len(players_df)} player-frame, "
                  f"{len(ball_df)} ball-frame, teams={metadata['teams']}")

        if not metadata['teams']:
            self._log("WARNING: hech qanday team aniqlanmadi - tahlil to'xtatildi")
            return {'metadata': metadata, 'error': 'no_teams_detected'}

        # 2. Track filter (eng muhim) - 50 ID -> 13
        self._log("Track filter (qisqa va beqaror IDlarni tozalash)...")
        track_filter = TrackFilter(
            min_lifetime_frames=self.config.min_track_lifetime_frames,
            min_distance_m=self.config.min_track_distance_m,
            min_appearance_pct=self.config.min_track_appearance_pct,
        )
        players_df, filter_stats = track_filter.filter(players_df, total_frames)
        metadata['track_filter_stats'] = filter_stats

        # 3. Team direction detection
        self._log("Hujum yo'nalishi aniqlanmoqda...")
        direction_detector = TeamDirectionDetector(court_length_y=self.config_court_length_y())
        directions = direction_detector.detect(players_df, fps=self.fps)
        metadata['team_directions'] = directions
        self._log(f"  {direction_detector.info()}")

        # Heatmap uchun normalize qilingan versiya (heatmap analyzer ichida ishlatiladi)
        if self.config.normalize_team_direction:
            players_df_normalized = direction_detector.normalize_y(players_df)
        else:
            players_df_normalized = players_df

        results = {'metadata': metadata}

        # 4. Possession (raw players_df bilan, normalize qilinmagan)
        self._log("1/5 Possession...")
        results['possession'] = PossessionAnalyzer(
            court_length=self.config_court_length_y()
        ).analyze(players_df, ball_df, metadata)

        # 5. Speed profile (config'dan threshold)
        self._log("2/5 Speed profile...")
        results['speed'] = SpeedProfileAnalyzer(
            sprint_threshold_kmh=self.config.sprint_threshold_kmh,
            sprint_min_duration_s=self.config.sprint_min_duration_s,
            walking_max=self.config.walking_max_kmh,
            jogging_max=self.config.jogging_max_kmh,
            running_max=self.config.running_max_kmh,
            high_speed_threshold_kmh=14.4,
            accel_threshold=2.0,
            decel_threshold=-2.0,
        ).analyze(players_df, metadata)

        # 6. Heatmap (normalize qilingan players_df bilan)
        self._log("3/5 Heatmap (PNG ham yaratiladi)...")
        results['heatmap'] = HeatmapGenerator(
            court_width=self.court_length,
            court_length=self.config_court_length_y(),
            grid=(self.config.heatmap_grid_x, self.config.heatmap_grid_y),
        ).analyze(players_df_normalized, metadata, output_dir=self.output_dir)

        # 7. Pass network
        self._log("4/5 Pass tarmog'i (PNG ham yaratiladi)...")
        results['passes'] = PassNetworkAnalyzer(
            max_gap_seconds=self.config.pass_max_gap_seconds,
        ).analyze(ball_df, players_df, metadata, output_dir=self.output_dir)

        # 8. Pressing
        self._log("5/5 Pressing intensity...")
        results['pressing'] = PressingAnalyzer(
            high_press_distance_m=self.config.high_press_distance_m,
        ).analyze(players_df, ball_df, metadata)

        # 9. Comparator (config thresholds)
        self._log("Kuchli/zaif tomonlarni aniqlash...")
        comp = TeamComparator()
        comp.THRESHOLDS = {
            'possession_dominant': self.config.possession_dominant_pct,
            'sprint_advantage': self.config.sprint_advantage_count,
            'pressure_high': self.config.pressure_high_distance_m,
            'pressure_low': self.config.pressure_low_distance_m,
            'pass_acc_good': self.config.pass_acc_good_pct,
            'distance_advantage': self.config.distance_advantage_m,
        }
        results['comparison'] = comp.compare(
            results['possession'], results['speed'], results['heatmap'],
            results['passes'], results['pressing'], metadata['teams']
        )

        elapsed = time.time() - t0
        self._log(f"Tahlil tugadi ({elapsed:.1f} sek)")
        return results

    def config_court_length_y(self):
        """
        court_width metada y-axis uzunligini bildiradi (original konvensiya).
        Foydalanuvchining xato yuborishini tuzatamiz.
        """
        return self.court_width

    def write_reports(self, results):
        reporter = Reporter(output_dir=self.output_dir)
        json_path = reporter.write_json(results)
        html_path = reporter.write_html(results)
        self._log(f"JSON: {json_path}")
        self._log(f"HTML: {html_path}")
        return {'json': json_path, 'html': html_path}

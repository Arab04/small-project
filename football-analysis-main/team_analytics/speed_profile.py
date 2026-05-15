"""
Speed Profile Analyzer — Professional GPS-like per-player stats
================================================================
Catapult / STATSports / Polar uslubida har bir o'yinchi uchun:
  - Total distance (m/km)
  - High-speed running distance (HSR)
  - Sprint distance & count
  - Speed zone distribution
  - Accelerations / decelerations
  - Distance per minute (intensity)
  - Work rate (m/min)
  - Peak speed
  - Sprint details (start, duration, max speed)
"""
import numpy as np
import pandas as pd


class SpeedProfileAnalyzer:
    # Fizik limitlar — hech qanday futbolchi bundan oshmasligi kerak
    MAX_REALISTIC_SPEED_KMH = 35.0     # Haaland/Mbappé = ~36.5, lekin video tracking uchun 35 yetarli
    MAX_REALISTIC_ACCEL_MS2 = 5.0      # Futbolchi max ~4 m/s², 5 = xavfsiz limit
    MIN_REALISTIC_DECEL_MS2 = -5.0     # Tez to'xtash limiti

    # Acceleration event detection — faqat haqiqiy tezlanish/sekinlashishlar
    ACCEL_MIN_SUSTAINED_FRAMES = 3     # Kamida 3 frame davom etishi kerak (noise emas)

    def __init__(self, sprint_threshold_kmh=18.0, sprint_min_duration_s=0.5,
                 walking_max=6.0, jogging_max=12.0, running_max=18.0,
                 high_speed_threshold_kmh=14.4,
                 accel_threshold=2.0, decel_threshold=-2.0):
        """
        Args:
            sprint_threshold_kmh: Sprint tezlik chegarasi
            high_speed_threshold_kmh: Yuqori tezlikda yugurish chegarasi (HSR)
            accel_threshold: m/s² — sezilarli tezlanish
            decel_threshold: m/s² — sezilarli sekinlashish (manfiy)
        """
        self.sprint_threshold = sprint_threshold_kmh
        self.sprint_min_duration_s = sprint_min_duration_s
        self.high_speed_threshold = high_speed_threshold_kmh
        self.accel_threshold = accel_threshold
        self.decel_threshold = decel_threshold
        self.bands = [
            ('walking', 0, walking_max),
            ('jogging', walking_max, jogging_max),
            ('running', jogging_max, running_max),
            ('sprinting', running_max, 999),
        ]

    @staticmethod
    def _smooth_speeds(speeds, window=11):
        """
        2-bosqichli smoothing:
        1) Median filter (window=11) — tracking jitter spike'larni yo'qotadi
        2) Rolling mean (window=5) — qolgan noise'ni tekislaydi
        Natija: silliq, lekin haqiqiy peak'larni saqlab qoladi.
        """
        if len(speeds) < 5:
            return speeds
        from scipy.ndimage import median_filter
        try:
            # 1-bosqich: Median — spike'lar ketadi
            smoothed = median_filter(speeds, size=min(window, len(speeds)))
            # 2-bosqich: Rolling mean — qolgan tebranishlar
            kern = min(5, len(smoothed))
            kernel = np.ones(kern) / kern
            smoothed = np.convolve(smoothed, kernel, mode='same')
            return smoothed
        except ImportError:
            kernel = np.ones(min(window, len(speeds))) / min(window, len(speeds))
            return np.convolve(speeds, kernel, mode='same')

    def _cap_speed(self, speeds):
        """Fizik jihatdan imkonsiz tezliklarni cheklash."""
        return np.clip(speeds, 0, self.MAX_REALISTIC_SPEED_KMH)

    def analyze(self, players_df, metadata):
        fps = metadata['fps']
        teams = metadata['teams']
        duration_s = metadata.get('duration_s', 1)
        results = {}
        valid = players_df[players_df['team'] > 0].copy()

        for team in teams:
            team_data = valid[valid['team'] == team]
            if team_data.empty:
                results[f'team_{team}'] = self._empty_result()
                continue

            non_zero = team_data[team_data['speed'] > 0]['speed']
            team_results = {
                'max_speed_kmh': round(float(team_data['speed'].max()), 2),
                'avg_speed_kmh': round(float(non_zero.mean()), 2) if len(non_zero) > 0 else 0,
                'total_distance_m': self._total_distance(team_data),
            }

            # Speed zone distribution
            band_pcts = {}
            band_distances = {}
            total = len(team_data)
            for name, lo, hi in self.bands:
                mask = (team_data['speed'] >= lo) & (team_data['speed'] < hi)
                count = int(mask.sum())
                band_pcts[name] = round(100 * count / total, 1) if total > 0 else 0
                # Zone dagi masofa (taxminiy)
                zone_speeds = team_data.loc[mask, 'speed']
                band_distances[name] = round(float(zone_speeds.sum() / 3.6 / fps), 1) if len(zone_speeds) > 0 else 0
            team_results['speed_band_pct'] = band_pcts
            team_results['speed_band_distance_m'] = band_distances

            # HSR (High Speed Running) distance
            hsr_mask = team_data['speed'] >= self.high_speed_threshold
            hsr_speeds = team_data.loc[hsr_mask, 'speed']
            team_results['high_speed_running_distance_m'] = round(float(hsr_speeds.sum() / 3.6 / fps), 1)

            # Sprint distance
            sprint_mask = team_data['speed'] >= self.sprint_threshold
            sprint_speeds = team_data.loc[sprint_mask, 'speed']
            team_results['sprint_distance_m'] = round(float(sprint_speeds.sum() / 3.6 / fps), 1)

            # Sprint count
            sprints = self._count_sprints(team_data, fps)
            team_results['total_sprints'] = sum(sprints.values())
            team_results['players_count'] = len(sprints)
            team_results['avg_sprints_per_player'] = round(
                np.mean(list(sprints.values())), 2) if sprints else 0
            team_results['top_sprinter'] = self._top_sprinter(sprints)

            # Work rate (m/min)
            total_dist = team_results['total_distance_m']
            team_results['work_rate_m_per_min'] = round(total_dist / (duration_s / 60), 1) if duration_s > 0 else 0

            # Per-player GPS stats (professional)
            team_results['per_player'] = self._per_player_gps_stats(team_data, fps, duration_s)

            results[f'team_{team}'] = team_results

        return results

    def _total_distance(self, team_data):
        per_player_max = team_data.groupby('player_id')['distance'].max()
        return round(float(per_player_max.sum()), 1)

    def _per_player_gps_stats(self, team_data, fps, match_duration_s):
        """
        Professional GPS tracker uslubida har bir o'yinchi statistikasi.
        """
        stats = {}
        for pid, pdata in team_data.groupby('player_id'):
            pdata = pdata.sort_values('frame')
            raw_speeds = pdata['speed'].values
            # Smooth + cap — tracking noise'ni yo'qotish
            speeds = self._cap_speed(self._smooth_speeds(raw_speeds))
            non_zero = speeds[speeds > 0]

            # Asosiy ko'rsatkichlar
            total_distance = round(float(pdata['distance'].max()), 1) if len(pdata) > 0 else 0
            frames_tracked = int(len(pdata))
            time_on_field_s = frames_tracked / fps
            time_on_field_min = time_on_field_s / 60

            player_stats = {
                # Asosiy
                'max_speed_kmh': round(float(speeds.max()), 2) if len(speeds) > 0 else 0,
                'avg_speed_kmh': round(float(non_zero.mean()), 2) if len(non_zero) > 0 else 0,
                'total_distance_m': total_distance,
                'total_distance_km': round(total_distance / 1000, 3),
                'frames_tracked': frames_tracked,
                'time_on_field_min': round(time_on_field_min, 1),

                # Intensivlik (0.01 min = 0.6 sek dan keyin hisoblash)
                'distance_per_min': round(total_distance / time_on_field_min, 1) if time_on_field_min > 0.01 else 0,

                # Speed zone distances
                'zone_distances_m': {},
                'zone_time_pct': {},

                # HSR & Sprint
                'high_speed_running_distance_m': 0,
                'sprint_distance_m': 0,
                'sprint_count': 0,
                'sprint_details': [],

                # Accelerations
                'accelerations': 0,
                'decelerations': 0,
                'max_acceleration': 0,
                'max_deceleration': 0,
            }

            # Speed zone taqsimoti
            for name, lo, hi in self.bands:
                mask = (speeds >= lo) & (speeds < hi)
                count = int(mask.sum())
                zone_speeds_in_band = speeds[mask]
                player_stats['zone_distances_m'][name] = round(float(zone_speeds_in_band.sum() / 3.6 / fps), 1)
                player_stats['zone_time_pct'][name] = round(100 * count / len(speeds), 1) if len(speeds) > 0 else 0

            # HSR distance
            hsr_mask = speeds >= self.high_speed_threshold
            player_stats['high_speed_running_distance_m'] = round(float(speeds[hsr_mask].sum() / 3.6 / fps), 1)

            # Sprint distance
            sprint_mask = speeds >= self.sprint_threshold
            player_stats['sprint_distance_m'] = round(float(speeds[sprint_mask].sum() / 3.6 / fps), 1)

            # Sprint details — smoothed speeds bilan aniqlash (raw emas!)
            sprints = self._detect_sprints_from_smoothed(speeds, pdata['frame'].values, fps)
            player_stats['sprint_count'] = len(sprints)
            player_stats['sprint_details'] = sprints

            # Acceleration/Deceleration
            accel_stats = self._compute_accelerations(speeds, fps)
            player_stats['accelerations'] = accel_stats['accel_count']
            player_stats['decelerations'] = accel_stats['decel_count']
            player_stats['max_acceleration'] = accel_stats['max_accel']
            player_stats['max_deceleration'] = accel_stats['max_decel']

            stats[int(pid)] = player_stats

        return stats

    def _detect_sprints_from_smoothed(self, speeds, frames, fps):
        """Smoothed tezliklar asosida sprint aniqlash — aniqroq natija."""
        min_frames = max(1, int(self.sprint_min_duration_s * fps))
        sprints = []
        in_sprint = False
        sprint_start = 0
        sprint_max_speed = 0

        for i, speed in enumerate(speeds):
            if speed >= self.sprint_threshold:
                if not in_sprint:
                    sprint_start = i
                    sprint_max_speed = speed
                    in_sprint = True
                else:
                    sprint_max_speed = max(sprint_max_speed, speed)
            else:
                if in_sprint:
                    duration_frames = i - sprint_start
                    if duration_frames >= min_frames:
                        sprint_speeds = speeds[sprint_start:i]
                        sprints.append({
                            'start_frame': int(frames[sprint_start]),
                            'end_frame': int(frames[i - 1]),
                            'duration_s': round(duration_frames / fps, 2),
                            'max_speed_kmh': round(float(sprint_max_speed), 1),
                            'avg_speed_kmh': round(float(sprint_speeds.mean()), 1),
                            'distance_m': round(float(sprint_speeds.sum() / 3.6 / fps), 1),
                        })
                    in_sprint = False

        # Oxirgi sprint
        if in_sprint:
            duration_frames = len(speeds) - sprint_start
            if duration_frames >= min_frames:
                sprint_speeds = speeds[sprint_start:]
                sprints.append({
                    'start_frame': int(frames[sprint_start]),
                    'end_frame': int(frames[-1]),
                    'duration_s': round(duration_frames / fps, 2),
                    'max_speed_kmh': round(float(sprint_max_speed), 1),
                    'avg_speed_kmh': round(float(sprint_speeds.mean()), 1),
                    'distance_m': round(float(sprint_speeds.sum() / 3.6 / fps), 1),
                })

        return sprints

    def _detect_sprints_detailed(self, pdata, fps):
        """Har bir sprintning batafsil ma'lumotlari."""
        speeds = pdata['speed'].values
        frames = pdata['frame'].values
        min_frames = max(1, int(self.sprint_min_duration_s * fps))

        sprints = []
        in_sprint = False
        sprint_start = 0
        sprint_max_speed = 0

        for i, speed in enumerate(speeds):
            if speed >= self.sprint_threshold:
                if not in_sprint:
                    sprint_start = i
                    sprint_max_speed = speed
                    in_sprint = True
                else:
                    sprint_max_speed = max(sprint_max_speed, speed)
            else:
                if in_sprint:
                    duration_frames = i - sprint_start
                    if duration_frames >= min_frames:
                        sprint_speeds = speeds[sprint_start:i]
                        sprints.append({
                            'start_frame': int(frames[sprint_start]),
                            'end_frame': int(frames[i - 1]),
                            'duration_s': round(duration_frames / fps, 2),
                            'max_speed_kmh': round(float(sprint_max_speed), 1),
                            'avg_speed_kmh': round(float(sprint_speeds.mean()), 1),
                            'distance_m': round(float(sprint_speeds.sum() / 3.6 / fps), 1),
                        })
                    in_sprint = False

        # Oxirgi sprint
        if in_sprint:
            duration_frames = len(speeds) - sprint_start
            if duration_frames >= min_frames:
                sprint_speeds = speeds[sprint_start:]
                sprints.append({
                    'start_frame': int(frames[sprint_start]),
                    'end_frame': int(frames[-1]),
                    'duration_s': round(duration_frames / fps, 2),
                    'max_speed_kmh': round(float(sprint_max_speed), 1),
                    'avg_speed_kmh': round(float(sprint_speeds.mean()), 1),
                    'distance_m': round(float(sprint_speeds.sum() / 3.6 / fps), 1),
                })

        return sprints

    def _compute_accelerations(self, speeds, fps):
        """
        Tezlanish va sekinlashish soni va max qiymatlari.
        Faqat DAVOMLI tezlanishlarni hisoblaydi (kamida ACCEL_MIN_SUSTAINED_FRAMES frame).
        Yakka frame spike'lar = noise, hisobga olinmaydi.
        """
        if len(speeds) < 10:
            return {'accel_count': 0, 'decel_count': 0, 'max_accel': 0, 'max_decel': 0}

        # Speed allaqachon smoothed va capped
        speeds_ms = speeds / 3.6
        dt = 1.0 / fps

        # Kuchli smoothing: 15-frame rolling average
        window = min(15, len(speeds_ms))
        if window >= 3:
            kernel = np.ones(window) / window
            speeds_ms_smooth = np.convolve(speeds_ms, kernel, mode='same')
        else:
            speeds_ms_smooth = speeds_ms

        accelerations = np.diff(speeds_ms_smooth) / dt  # m/s²

        # Fizik limitlar
        clipped = np.clip(accelerations,
                          self.MIN_REALISTIC_DECEL_MS2,
                          self.MAX_REALISTIC_ACCEL_MS2)

        # Davomli tezlanish eventlarni hisoblash (sprint detection kabi)
        min_frames = self.ACCEL_MIN_SUSTAINED_FRAMES
        accel_count = 0
        decel_count = 0
        max_accel = 0.0
        max_decel = 0.0

        # Tezlanish eventlari
        in_accel = 0
        current_max = 0.0
        for a in clipped:
            if a > self.accel_threshold:
                in_accel += 1
                current_max = max(current_max, a)
            else:
                if in_accel >= min_frames:
                    accel_count += 1
                    max_accel = max(max_accel, current_max)
                in_accel = 0
                current_max = 0.0
        if in_accel >= min_frames:
            accel_count += 1
            max_accel = max(max_accel, current_max)

        # Sekinlashish eventlari
        in_decel = 0
        current_min = 0.0
        for a in clipped:
            if a < self.decel_threshold:
                in_decel += 1
                current_min = min(current_min, a)
            else:
                if in_decel >= min_frames:
                    decel_count += 1
                    max_decel = min(max_decel, current_min)
                in_decel = 0
                current_min = 0.0
        if in_decel >= min_frames:
            decel_count += 1
            max_decel = min(max_decel, current_min)

        return {
            'accel_count': accel_count,
            'decel_count': decel_count,
            'max_accel': round(float(max_accel), 2),
            'max_decel': round(float(max_decel), 2),
        }

    def _count_sprints(self, team_data, fps):
        min_frames = max(1, int(self.sprint_min_duration_s * fps))
        sprints = {}
        for pid, pdata in team_data.groupby('player_id'):
            pdata = pdata.sort_values('frame')
            speeds = pdata['speed'].values
            is_sprint = speeds > self.sprint_threshold
            count = 0
            current = 0
            for s in is_sprint:
                if s:
                    current += 1
                else:
                    if current >= min_frames:
                        count += 1
                    current = 0
            if current >= min_frames:
                count += 1
            sprints[int(pid)] = count
        return sprints

    def _top_sprinter(self, sprints):
        if not sprints:
            return None
        pid = max(sprints, key=sprints.get)
        return {'player_id': pid, 'sprint_count': sprints[pid]}

    def _empty_result(self):
        return {
            'max_speed_kmh': 0, 'avg_speed_kmh': 0, 'total_distance_m': 0,
            'speed_band_pct': {n: 0 for n, _, _ in self.bands},
            'speed_band_distance_m': {n: 0 for n, _, _ in self.bands},
            'high_speed_running_distance_m': 0,
            'sprint_distance_m': 0,
            'total_sprints': 0, 'players_count': 0, 'avg_sprints_per_player': 0,
            'top_sprinter': None, 'work_rate_m_per_min': 0,
            'per_player': {},
        }

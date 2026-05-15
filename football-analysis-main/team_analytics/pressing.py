"""Pressing Intensity Analyzer"""
import numpy as np
import pandas as pd


class PressingAnalyzer:
    def __init__(self, high_press_distance_m=5.0):
        self.high_press_distance = high_press_distance_m

    def analyze(self, players_df, ball_df, metadata):
        teams = metadata['teams']
        results = {}

        pressure_records = []
        valid = players_df.dropna(subset=['x', 'y'])
        ball_with_owner = ball_df[ball_df['owner_id'] != -1]

        for _, brow in ball_with_owner.iterrows():
            frame = brow['frame']
            owner_team = brow['owner_team']
            owner_id = brow['owner_id']

            owner_row = valid[(valid['frame'] == frame) & (valid['player_id'] == owner_id)]
            if owner_row.empty:
                continue
            ox, oy = owner_row.iloc[0]['x'], owner_row.iloc[0]['y']

            opponents = valid[(valid['frame'] == frame) &
                               (valid['team'] != owner_team) & (valid['team'] > 0)]
            if opponents.empty:
                continue

            distances = np.sqrt((opponents['x'] - ox)**2 + (opponents['y'] - oy)**2)
            min_dist = float(distances.min())
            defender_team = int(opponents['team'].iloc[0])

            pressure_records.append({
                'frame': int(frame),
                'owner_team': int(owner_team),
                'defender_team': defender_team,
                'distance': min_dist,
                'x': float(ox), 'y': float(oy),
            })

        if not pressure_records:
            for team in teams:
                results[f'team_{team}'] = self._empty()
            return results

        pdf = pd.DataFrame(pressure_records)
        cl = metadata.get('court_width', 68)  # y-axis length

        for team in teams:
            defending = pdf[pdf['defender_team'] == team]
            if defending.empty:
                results[f'team_{team}'] = self._empty()
                continue

            high_press = (defending['distance'] < self.high_press_distance).sum()

            zone_thirds = {'low_block': 0, 'mid_press': 0, 'high_press_zone': 0}
            for _, r in defending.iterrows():
                y = r['y']
                if y < cl / 3:
                    zone_thirds['low_block'] += 1
                elif y < 2 * cl / 3:
                    zone_thirds['mid_press'] += 1
                else:
                    zone_thirds['high_press_zone'] += 1
            total = sum(zone_thirds.values())
            zone_pct = (
                {k: round(100 * v / total, 1) for k, v in zone_thirds.items()}
                if total > 0 else {k: 0 for k in zone_thirds}
            )

            results[f'team_{team}'] = {
                'avg_pressure_distance_m': round(float(defending['distance'].mean()), 2),
                'median_pressure_distance_m': round(float(defending['distance'].median()), 2),
                'high_press_pct': round(100 * high_press / len(defending), 1),
                'pressing_zone_pct': zone_pct,
                'frames_analyzed': int(len(defending)),
            }

        return results

    def _empty(self):
        return {
            'avg_pressure_distance_m': None,
            'median_pressure_distance_m': None,
            'high_press_pct': 0,
            'pressing_zone_pct': {'low_block': 0, 'mid_press': 0, 'high_press_zone': 0},
            'frames_analyzed': 0,
        }

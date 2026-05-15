"""
Possession Analyzer
"""
import numpy as np
import pandas as pd


class PossessionAnalyzer:
    def __init__(self, court_length=68.0):
        self.court_length = court_length
        self.thirds = {
            'defensive': (0, court_length / 3),
            'middle': (court_length / 3, 2 * court_length / 3),
            'attacking': (2 * court_length / 3, court_length),
        }

    def analyze(self, players_df, ball_df, metadata):
        results = {}
        teams = metadata['teams']
        fps = metadata['fps']

        owner_teams = ball_df['owner_team'].values
        valid_mask = np.isin(owner_teams, teams)
        valid_total = int(valid_mask.sum())

        for team in teams:
            team_frames = int((owner_teams == team).sum())
            results[f'team_{team}'] = {
                'possession_pct': round(100 * team_frames / valid_total, 2) if valid_total > 0 else 0,
                'possession_seconds': round(team_frames / fps, 1),
            }

        for team in teams:
            owner_seq = (owner_teams == team).astype(int)
            runs = []
            current = 0
            for v in owner_seq:
                if v == 1:
                    current += 1
                else:
                    if current > 0:
                        runs.append(current)
                    current = 0
            if current > 0:
                runs.append(current)

            min_frames = max(1, int(0.3 * fps))
            meaningful = [r for r in runs if r >= min_frames]
            results[f'team_{team}']['num_possessions'] = len(meaningful)
            results[f'team_{team}']['avg_possession_duration_s'] = round(
                np.mean(meaningful) / fps, 2) if meaningful else 0
            results[f'team_{team}']['max_possession_duration_s'] = round(
                max(meaningful) / fps, 2) if meaningful else 0

        valid = players_df.dropna(subset=['x', 'y'])
        for team in teams:
            team_pos = valid[valid['team'] == team]
            zone_counts = {}
            for zone_name, (lo, hi) in self.thirds.items():
                count = int(((team_pos['y'] >= lo) & (team_pos['y'] < hi)).sum())
                zone_counts[zone_name] = count
            total_zone = sum(zone_counts.values())
            zone_pct = (
                {k: round(100 * v / total_zone, 1) for k, v in zone_counts.items()}
                if total_zone > 0 else {k: 0 for k in zone_counts}
            )
            results[f'team_{team}']['zone_distribution_pct'] = zone_pct
            results[f'team_{team}']['avg_field_position_y'] = (
                round(float(team_pos['y'].mean()), 2) if len(team_pos) > 0 else None
            )

        if not ball_df.empty:
            ball_valid = ball_df.dropna(subset=['y'])
            for team in teams:
                in_attack = ball_valid[
                    (ball_valid['owner_team'] == team) &
                    (ball_valid['y'] >= self.thirds['attacking'][0])
                ]
                results[f'team_{team}']['attacking_third_possession_s'] = round(
                    len(in_attack) / fps, 1)

        return results

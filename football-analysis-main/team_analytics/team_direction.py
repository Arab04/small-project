"""
Team Direction Detector
=======================
Qaysi kamanda qaysi tomonga hujum qilishini aniqlaydi.

Muammo:
    Ikki kamanda heatmap'i deyarli bir xil chiqyapti (markazda to'planib).
    Sabab: tahlilda "Team 1 ham, Team 2 ham bir xil y-axis da pozitsiyaga
    ega" deb hisoblanyapti. Real futbolda esa ular qarama-qarshi tomonlarda
    o'ynaydi.

Yondashuv:
    Birinchi N freymda har kamandaning markaz koordinatasini hisoblash.
    Qaysi kamanda kichikroq y'da -> "pastdan yuqoriga hujum qiluvchi"
    Qaysi kamanda kattaroq y'da -> "yuqoridan pastga hujum qiluvchi"
"""
import numpy as np


class TeamDirectionDetector:
    def __init__(self, court_length_y=68.0):
        self.court_length_y = court_length_y
        self.team_centers = {}
        self.team_attack_direction = {}

    def detect(self, players_df, first_n_seconds=5, fps=24):
        max_frame = first_n_seconds * fps
        early = players_df[
            (players_df['frame'] < max_frame) &
            players_df['y'].notna() &
            (players_df['team'] > 0)
        ]

        if early.empty:
            self.team_attack_direction = {1: +1, 2: -1}
            return self.team_attack_direction

        team_means = early.groupby('team')['y'].mean().to_dict()

        if len(team_means) < 2:
            self.team_attack_direction = {1: +1, 2: -1}
            return self.team_attack_direction

        sorted_teams = sorted(team_means.items(), key=lambda x: x[1])
        lower_team = sorted_teams[0][0]
        upper_team = sorted_teams[1][0]

        self.team_attack_direction[lower_team] = +1
        self.team_attack_direction[upper_team] = -1
        self.team_centers = team_means

        return self.team_attack_direction

    def normalize_y(self, players_df):
        """Hujum yo'nalishlarini bir xil ('yuqoriga') qilish"""
        if not self.team_attack_direction:
            return players_df

        df = players_df.copy()
        for team, direction in self.team_attack_direction.items():
            if direction == -1:
                mask = (df['team'] == team) & df['y'].notna()
                df.loc[mask, 'y'] = self.court_length_y - df.loc[mask, 'y']

        return df

    def info(self):
        if not self.team_attack_direction:
            return "Team direction not detected"
        lines = []
        for team, direction in sorted(self.team_attack_direction.items()):
            arrow = "up" if direction == +1 else "down"
            center = self.team_centers.get(team, 0)
            lines.append(f"Team {team}: y_center={center:.1f}, attack {arrow}")
        return " | ".join(lines)

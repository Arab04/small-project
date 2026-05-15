"""Heatmap Generator"""
import os
import numpy as np
import pandas as pd

try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    HAS_MPL = True
except ImportError:
    HAS_MPL = False


class HeatmapGenerator:
    def __init__(self, court_width=23.32, court_length=68.0, grid=(20, 20)):
        self.court_width = court_width
        self.court_length = court_length
        self.grid_x, self.grid_y = grid

    def analyze(self, players_df, metadata, output_dir=None):
        teams = metadata['teams']
        results = {}
        valid = players_df.dropna(subset=['x', 'y'])

        for team in teams:
            team_data = valid[valid['team'] == team]
            if team_data.empty:
                results[f'team_{team}'] = {'centroid': None, 'spread_m': 0}
                continue

            heatmap, _, _ = np.histogram2d(
                team_data['x'], team_data['y'],
                bins=[self.grid_x, self.grid_y],
                range=[[0, self.court_width], [0, self.court_length]]
            )
            cx = float(team_data['x'].mean())
            cy = float(team_data['y'].mean())
            spread = float(np.sqrt(team_data['x'].std()**2 + team_data['y'].std()**2))

            results[f'team_{team}'] = {
                'centroid': {'x': round(cx, 2), 'y': round(cy, 2)},
                'spread_m': round(spread, 2),
                'heatmap_grid': heatmap.tolist(),
                'grid_shape': [self.grid_x, self.grid_y],
            }

            if output_dir and HAS_MPL:
                self._save_heatmap_png(
                    heatmap, team,
                    os.path.join(output_dir, f'heatmap_team_{team}.png')
                )

        if output_dir and HAS_MPL:
            top_players = (
                valid.groupby('player_id')['frame'].count()
                .sort_values(ascending=False).head(5).index.tolist()
            )
            for pid in top_players:
                pdata = valid[valid['player_id'] == pid]
                if len(pdata) < 10:
                    continue
                team = int(pdata['team'].iloc[0])
                self._save_player_heatmap(pdata, pid, team, output_dir)

        return results

    def _save_heatmap_png(self, heatmap, team, path):
        fig, ax = plt.subplots(figsize=(8, 5))
        im = ax.imshow(
            heatmap.T,
            extent=[0, self.court_width, 0, self.court_length],
            origin='lower', cmap='hot', aspect='auto', interpolation='gaussian'
        )
        ax.set_title(f'Team {team} - Position Heatmap', fontsize=14)
        ax.set_xlabel('X (m)')
        ax.set_ylabel('Y (m, attack direction)')
        for frac in [1/3, 2/3]:
            ax.axhline(self.court_length * frac, color='cyan',
                       linestyle='--', alpha=0.4, linewidth=1)
        plt.colorbar(im, ax=ax, label='Density')
        plt.tight_layout()
        plt.savefig(path, dpi=100, bbox_inches='tight')
        plt.close(fig)

    def _save_player_heatmap(self, pdata, pid, team, output_dir):
        path = os.path.join(output_dir, f'heatmap_player_{pid}_team{team}.png')
        fig, ax = plt.subplots(figsize=(8, 5))
        ax.scatter(pdata['x'], pdata['y'], alpha=0.3, s=8, c='red')
        ax.set_xlim(0, self.court_width)
        ax.set_ylim(0, self.court_length)
        ax.set_title(f'Player {pid} (Team {team}) - Movement', fontsize=12)
        ax.set_xlabel('X (m)')
        ax.set_ylabel('Y (m)')
        for frac in [1/3, 2/3]:
            ax.axhline(self.court_length * frac, color='gray',
                       linestyle='--', alpha=0.4)
        ax.grid(True, alpha=0.2)
        plt.tight_layout()
        plt.savefig(path, dpi=80, bbox_inches='tight')
        plt.close(fig)

"""
Data Extractor
==============
`tracks` obyektidagi nested dict'larni qulay analitika uchun
pandas DataFrame'larga aylantiradi.
"""
import numpy as np
import pandas as pd


class DataExtractor:
    def __init__(self, fps=24, court_width=68.0, court_length=23.32):
        self.fps = fps
        self.court_width = court_width
        self.court_length = court_length

    def extract(self, tracks, team_ball_control=None):
        rows = []
        for frame_num, frame_players in enumerate(tracks['players']):
            for pid, info in frame_players.items():
                pos = info.get('position_transformed')
                if pos is None:
                    x, y = np.nan, np.nan
                else:
                    x, y = float(pos[0]), float(pos[1])
                rows.append({
                    'frame': frame_num,
                    'time_s': frame_num / self.fps,
                    'player_id': pid,
                    'team': info.get('team', 0),
                    'x': x,
                    'y': y,
                    'speed': float(info.get('speed', 0.0)),
                    'distance': float(info.get('distance', 0.0)),
                    'has_ball': bool(info.get('has_ball', False)),
                })
        players_df = pd.DataFrame(rows)

        ball_rows = []
        for frame_num, ball_frame in enumerate(tracks['ball']):
            ball_info = ball_frame.get(1, {})
            pos = ball_info.get('position_transformed')
            if pos is None:
                x, y = np.nan, np.nan
            else:
                x, y = float(pos[0]), float(pos[1])

            owner_id = -1
            owner_team = 0
            if frame_num < len(tracks['players']):
                for pid, p_info in tracks['players'][frame_num].items():
                    if p_info.get('has_ball'):
                        owner_id = pid
                        owner_team = p_info.get('team', 0)
                        break
            if owner_team == 0 and team_ball_control is not None and frame_num < len(team_ball_control):
                owner_team = int(team_ball_control[frame_num])

            ball_rows.append({
                'frame': frame_num,
                'time_s': frame_num / self.fps,
                'x': x, 'y': y,
                'owner_id': owner_id,
                'owner_team': owner_team,
            })
        ball_df = pd.DataFrame(ball_rows)

        metadata = {
            'fps': self.fps,
            'total_frames': len(tracks['players']),
            'duration_s': len(tracks['players']) / self.fps,
            'court_width': self.court_width,
            'court_length': self.court_length,
            'teams': sorted(players_df[players_df['team'] > 0]['team'].unique().tolist()) if not players_df.empty else [],
        }
        return players_df, ball_df, metadata

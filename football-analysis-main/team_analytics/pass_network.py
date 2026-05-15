"""
Pass Network Analyzer (Enhanced)
=================================
Position-aware pass detection — ID switching ga bardosh.

Asosiy yaxshilanish: ByteTrack 40-50 ID portlatadigan tracking xatolarida ham
real pass'larni topadi. Mantiq:
- Player A id=15 da to'p edi
- 0.5s tanaffus
- Player B id=87 da to'p paydo bo'ldi
- Lekin A va B pozitsiyasi 2m masofada (ID switch)
- => Bu pass emas, ID o'zgartirish - egalik davom etgan deb hisoblaymiz

Bu pass aniqligi va turnover sonini real qiymatlarga yaqinlashtiradi.
"""
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


# ID switching ehtimoli
ID_SWITCH_DISTANCE_M = 3.0       # 3m dan yaqin = ehtimol o'sha oyinchi
ID_SWITCH_TIME_GAP_S = 1.0       # 1s ichida ID o'zgargan bo'lsa


class PassNetworkAnalyzer:
    def __init__(self, max_gap_seconds=2.0,
                 id_switch_distance_m=ID_SWITCH_DISTANCE_M,
                 id_switch_time_gap_s=ID_SWITCH_TIME_GAP_S):
        self.max_gap_seconds = max_gap_seconds
        self.id_switch_distance_m = id_switch_distance_m
        self.id_switch_time_gap_s = id_switch_time_gap_s

    def analyze(self, ball_df, players_df, metadata, output_dir=None):
        fps = metadata['fps']
        teams = metadata['teams']
        max_gap_frames = int(self.max_gap_seconds * fps)
        max_id_switch_frames = int(self.id_switch_time_gap_s * fps)

        # Egalik segmentlarini chiqarish
        possessions = self._extract_possession_segments(ball_df)

        # Pozitsion ma'lumotlarni egalik segmentlariga qo'shish
        possessions = self._enrich_with_positions(possessions, players_df)

        # ID switching'ni filtrlab, segmentlarni birlashtirish
        possessions = self._merge_id_switches(possessions, max_id_switch_frames)

        passes = []
        turnovers = []

        for i in range(len(possessions) - 1):
            curr = possessions[i]
            nxt = possessions[i + 1]
            gap = nxt['start_frame'] - curr['end_frame']
            if gap > max_gap_frames:
                continue

            if curr['team'] == nxt['team'] and curr['player'] != nxt['player']:
                # Pozitsiyalar uzoq bo'lishi kerak - o'sha oyinchi emas
                if self._distance(curr.get('end_pos'), nxt.get('start_pos')) > 2.0:
                    passes.append({
                        'from': curr['player'], 'to': nxt['player'],
                        'team': curr['team'], 'frame': curr['end_frame'],
                    })
            elif curr['team'] != nxt['team']:
                turnovers.append({
                    'from_team': curr['team'], 'to_team': nxt['team'],
                    'frame': curr['end_frame'],
                })

        results = {}
        for team in teams:
            team_passes = [p for p in passes if p['team'] == team]
            team_lost = [t for t in turnovers if t['from_team'] == team]
            team_won = [t for t in turnovers if t['to_team'] == team]

            pass_pairs = {}
            from_counts, to_counts = {}, {}
            for p in team_passes:
                key = (p['from'], p['to'])
                pass_pairs[key] = pass_pairs.get(key, 0) + 1
                from_counts[p['from']] = from_counts.get(p['from'], 0) + 1
                to_counts[p['to']] = to_counts.get(p['to'], 0) + 1

            top_passer = max(from_counts.items(), key=lambda x: x[1]) if from_counts else None
            top_receiver = max(to_counts.items(), key=lambda x: x[1]) if to_counts else None

            attempts = len(team_passes) + len(team_lost)
            accuracy = round(100 * len(team_passes) / attempts, 1) if attempts > 0 else 0

            results[f'team_{team}'] = {
                'total_passes': len(team_passes),
                'pass_accuracy_pct': accuracy,
                'turnovers_lost': len(team_lost),
                'turnovers_won': len(team_won),
                'top_passer': {'player_id': int(top_passer[0]), 'count': top_passer[1]} if top_passer else None,
                'top_receiver': {'player_id': int(top_receiver[0]), 'count': top_receiver[1]} if top_receiver else None,
                'pass_pairs': [
                    {'from': int(k[0]), 'to': int(k[1]), 'count': v}
                    for k, v in sorted(pass_pairs.items(), key=lambda x: -x[1])[:20]
                ],
            }

        if output_dir and HAS_MPL:
            for team in teams:
                self._draw_pass_network(
                    team, results[f'team_{team}']['pass_pairs'],
                    players_df, metadata,
                    os.path.join(output_dir, f'pass_network_team_{team}.png')
                )

        return results

    def _extract_possession_segments(self, ball_df):
        segments = []
        prev_owner = -1
        prev_team = 0
        start_frame = -1
        for _, row in ball_df.iterrows():
            owner = int(row['owner_id'])
            team = int(row['owner_team'])
            frame = int(row['frame'])
            if owner != prev_owner:
                if prev_owner != -1 and start_frame != -1:
                    segments.append({
                        'player': prev_owner, 'team': prev_team,
                        'start_frame': start_frame, 'end_frame': frame - 1,
                    })
                if owner != -1:
                    start_frame = frame
                else:
                    start_frame = -1
                prev_owner = owner
                prev_team = team
        if prev_owner != -1 and start_frame != -1:
            segments.append({
                'player': prev_owner, 'team': prev_team,
                'start_frame': start_frame,
                'end_frame': int(ball_df['frame'].iloc[-1]),
            })
        return segments

    def _enrich_with_positions(self, possessions, players_df):
        """Har bir egalikning boshlanishi va oxiridagi pozitsiyani aniqlash"""
        valid = players_df.dropna(subset=['x', 'y'])
        for seg in possessions:
            pid = seg['player']
            sf, ef = seg['start_frame'], seg['end_frame']
            start_row = valid[(valid['player_id'] == pid) & (valid['frame'] == sf)]
            end_row = valid[(valid['player_id'] == pid) & (valid['frame'] == ef)]
            seg['start_pos'] = (
                (float(start_row.iloc[0]['x']), float(start_row.iloc[0]['y']))
                if not start_row.empty else None
            )
            seg['end_pos'] = (
                (float(end_row.iloc[0]['x']), float(end_row.iloc[0]['y']))
                if not end_row.empty else None
            )
        return possessions

    def _merge_id_switches(self, possessions, max_gap_frames):
        """
        Yondosh segmentlarni birlashtirish, agar:
        - Bir xil kamandadan
        - Vaqt bo'shlig'i kichik
        - Pozitsiyalar yaqin (ID switch alomati)
        """
        if not possessions:
            return possessions

        merged = [possessions[0]]
        for seg in possessions[1:]:
            prev = merged[-1]
            gap = seg['start_frame'] - prev['end_frame']
            same_team = seg['team'] == prev['team']
            close_pos = self._distance(prev.get('end_pos'), seg.get('start_pos')) < self.id_switch_distance_m

            if same_team and gap <= max_gap_frames and close_pos:
                # ID switch deb qarayapmiz - oldingisini uzaytiramiz
                prev['end_frame'] = seg['end_frame']
                if seg.get('end_pos') is not None:
                    prev['end_pos'] = seg['end_pos']
            else:
                merged.append(seg)
        return merged

    @staticmethod
    def _distance(p1, p2):
        if p1 is None or p2 is None:
            return float('inf')
        return float(np.sqrt((p1[0] - p2[0])**2 + (p1[1] - p2[1])**2))

    def _draw_pass_network(self, team, pass_pairs, players_df, metadata, path):
        if not pass_pairs:
            return
        team_data = players_df[(players_df['team'] == team) & players_df['x'].notna()]
        if team_data.empty:
            return
        avg_pos = team_data.groupby('player_id').agg({'x': 'mean', 'y': 'mean'}).to_dict('index')

        fig, ax = plt.subplots(figsize=(8, 6))
        court_w = metadata['court_width']
        court_l = metadata['court_length']
        ax.set_xlim(0, court_l)
        ax.set_ylim(0, court_w)
        ax.set_facecolor('#0a4d0a')
        for frac in [1/3, 2/3]:
            ax.axhline(court_w * frac, color='white', alpha=0.3, linewidth=0.8)

        max_count = max(p['count'] for p in pass_pairs)
        for p in pass_pairs:
            if p['from'] not in avg_pos or p['to'] not in avg_pos:
                continue
            x1, y1 = avg_pos[p['from']]['x'], avg_pos[p['from']]['y']
            x2, y2 = avg_pos[p['to']]['x'], avg_pos[p['to']]['y']
            width = 0.5 + 3 * (p['count'] / max_count)
            ax.plot([x1, x2], [y1, y2], color='yellow', alpha=0.5, linewidth=width)

        for pid, pos in avg_pos.items():
            ax.scatter(pos['x'], pos['y'], s=300, color='white',
                       edgecolor='black', zorder=3)
            ax.annotate(str(int(pid)), (pos['x'], pos['y']),
                        ha='center', va='center', fontsize=9, fontweight='bold')

        ax.set_title(f'Team {team} - Pass Network', color='white', fontsize=13)
        ax.set_xlabel('Field length (m)')
        ax.set_ylabel('Field width (m)')
        plt.tight_layout()
        plt.savefig(path, dpi=100, bbox_inches='tight', facecolor='#0a4d0a')
        plt.close(fig)

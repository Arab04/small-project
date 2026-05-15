"""
Track Filter
============
ByteTrack ID switching natijasida hosil bo'ladigan "fake" tracklarni filtrlaydi.

Muammo:
    Real futbolda har kamandada 11 oyinchi. Lekin ByteTrack default
    parametrlari bilan 30-50 unique ID hosil bo'ladi (oyinchi bir lahza
    obyektdan chiqib qaytsa, yangi ID oladi).

Yechim:
    Quyidagi mezonlardan birini qondirgan tracklar haqiqiy oyinchi:
    1. Yetarli uzun yashagan (default >= 1 sek)
    2. Yetarli masofa bosib o'tgan (default >= 1m)
    3. Umumiy freymning yetarli foizida ko'ringan (default >= 5%)

Effekt:
    50 ID -> 13-15 ID. Possession va pass detection sezilarli yaxshilanadi.
"""
import numpy as np
import pandas as pd


class TrackFilter:
    def __init__(self, min_lifetime_frames=24, min_distance_m=1.0,
                 min_appearance_pct=5.0):
        self.min_lifetime = min_lifetime_frames
        self.min_distance = min_distance_m
        self.min_appearance_pct = min_appearance_pct

    def filter(self, players_df, total_frames, verbose=True):
        """
        players_df ni filtrlab, faqat haqiqiy tracklarni qaytaradi.

        Returns:
            filtered_df, stats_dict
        """
        if players_df.empty:
            return players_df, {'kept': 0, 'filtered': 0}

        # Har track_id uchun statistika
        stats = []
        for pid, pdata in players_df.groupby('player_id'):
            lifetime = int(pdata['frame'].max() - pdata['frame'].min() + 1)
            appearances = len(pdata)
            distance = float(pdata['distance'].max()) if 'distance' in pdata else 0.0
            appearance_pct = 100.0 * appearances / total_frames if total_frames > 0 else 0
            team = int(pdata['team'].iloc[0]) if 'team' in pdata else 0

            stats.append({
                'player_id': pid,
                'team': team,
                'lifetime_frames': lifetime,
                'appearances': appearances,
                'distance_m': distance,
                'appearance_pct': appearance_pct,
            })

        stats_df = pd.DataFrame(stats)

        # Filter mezonlari
        keep_mask = (
            (stats_df['lifetime_frames'] >= self.min_lifetime) &
            (stats_df['distance_m'] >= self.min_distance) &
            (stats_df['appearance_pct'] >= self.min_appearance_pct)
        )
        kept_df = stats_df[keep_mask].copy()

        # Har team uchun eng yaxshi 11 tani tanlash (real futbolda 11+11)
        # "appearances" bo'yicha saralash — ko'proq ko'ringan = real o'yinchi
        MAX_PER_TEAM = 14  # 11 + 3 sub/overlap buffer
        final_ids = []
        for team_id in kept_df['team'].unique():
            team_kept = kept_df[kept_df['team'] == team_id]
            # Ko'p ko'ringan va ko'p masofa bosganlar = real o'yinchi
            team_kept = team_kept.sort_values(
                ['appearances', 'distance_m'], ascending=[False, False]
            )
            top_n = team_kept.head(MAX_PER_TEAM)
            final_ids.extend(top_n['player_id'].tolist())

        kept_ids = set(final_ids)
        filtered_df = players_df[players_df['player_id'].isin(kept_ids)].copy()

        result_stats = {
            'total_ids_before': len(stats_df),
            'passed_filter': len(kept_df),
            'kept_ids': len(kept_ids),
            'filtered_ids': len(stats_df) - len(kept_ids),
            'team_1_kept': len([x for x in final_ids if x in stats_df[stats_df['team'] == 1]['player_id'].values]),
            'team_2_kept': len([x for x in final_ids if x in stats_df[stats_df['team'] == 2]['player_id'].values]),
        }

        if verbose:
            print(f"[track_filter] {result_stats['total_ids_before']} ID -> "
                  f"{result_stats['kept_ids']} (Team1: {result_stats['team_1_kept']}, "
                  f"Team2: {result_stats['team_2_kept']})", flush=True)

        return filtered_df, result_stats

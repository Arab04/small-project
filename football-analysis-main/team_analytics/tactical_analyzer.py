"""
Tactical Analyzer — Trener uchun professional taktik tahlil
============================================================
Quyidagi metrikalarni hisoblaydi:

1. PPDA (Passes Per Defensive Action) — pressing intensivligi
2. Transitions — to'p yo'qotilgandan keyingi 5-8 soniya
3. Progressive Passes — maydonni oldinga olib borgan paslar
4. Recovery Map — to'p qayerda qaytarib olingani
5. Danger Zones — xavfli hududlar (raqibning zaif nuqtalari)
6. Player Vulnerability — kim pressga dosh berolmaydi
7. Fatigue Timeline — sprint intensivligi vaqt bo'yicha

Uchta asosiy savolga javob:
  - QAYERDA? (Raqib qayerda bo'shliq qoldiryapti?)
  - KIM ORQALI? (Qaysi o'yinchi pressingga dosh berolmaydi?)
  - QACHON? (Raqib qachon charchaydi yoki taktikani o'zgartiradi?)
"""
import numpy as np
import pandas as pd


class TacticalAnalyzer:
    """Trener uchun professional taktik tahlil moduli."""

    # Transition window — to'p yo'qotilgandan keyingi soniyalar
    TRANSITION_WINDOW_S = 6.0
    # Progressive pass — kamida shu metrga oldinga (y-axis bo'yicha)
    PROGRESSIVE_PASS_MIN_M = 5.0
    # Xavfli zona — oxirgi 1/3 (attacking third)
    DANGER_ZONE_Y_PCT = 0.667
    # Vulnerability — turnover soni shu dan oshsa, zaif
    VULNERABILITY_TURNOVER_MIN = 2
    # Fatigue — sprint sonini 5 minutlik oynada ko'rish
    FATIGUE_WINDOW_MINUTES = 5

    def analyze(self, players_df, ball_df, metadata, pass_data=None, pressing_data=None):
        """
        Barcha taktik metrikalarni hisoblash.

        Args:
            players_df: DataFrame — o'yinchi pozitsiyalari (frame, player_id, team, x, y, speed_kmh)
            ball_df: DataFrame — to'p egaligi (frame, owner_id, owner_team)
            metadata: dict — fps, teams, court_width/length
            pass_data: dict — PassNetworkAnalyzer natijasi (opsional)
            pressing_data: dict — PressingAnalyzer natijasi (opsional)

        Returns:
            dict — barcha taktik metrikalar
        """
        fps = metadata.get('fps', 24)
        teams = metadata.get('teams', [1, 2])
        court_length = metadata.get('court_width', 68.0)  # y-axis

        results = {}
        for team in teams:
            opp_team = [t for t in teams if t != team]
            opp = opp_team[0] if opp_team else team

            results[f'team_{team}'] = {
                'ppda': self._compute_ppda(ball_df, pass_data, team, opp),
                'transitions': self._compute_transitions(ball_df, players_df, fps, team, opp, court_length),
                'progressive_passes': self._compute_progressive_passes(ball_df, players_df, fps, team, court_length),
                'recovery_map': self._compute_recovery_map(ball_df, players_df, team, court_length),
                'danger_zones': self._compute_danger_zones(ball_df, players_df, team, opp, court_length),
                'player_vulnerability': self._compute_vulnerability(ball_df, players_df, fps, opp),
                'fatigue_timeline': self._compute_fatigue_timeline(players_df, fps, team),
            }

        return results

    def _compute_ppda(self, ball_df, pass_data, team, opp_team):
        """
        PPDA = Raqibning paslar soni / Jamoaning himoyaviy harakatlari soni
        Past PPDA = agressiv pressing (8-10 = yuqori, 14+ = past)
        """
        if pass_data is None:
            return {'ppda': None, 'interpretation': 'Ma\'lumot yetarli emas'}

        opp_passes = pass_data.get(f'team_{opp_team}', {}).get('total_passes', 0)
        # Defensive actions = turnovers won by this team
        turnovers_won = pass_data.get(f'team_{team}', {}).get('turnovers_won', 0)

        if turnovers_won == 0:
            return {'ppda': None, 'interpretation': 'Himoyaviy harakat topilmadi'}

        ppda = round(opp_passes / turnovers_won, 2)

        if ppda < 8:
            interp = 'Juda agressiv pressing (Gegenpressing uslubi)'
        elif ppda < 12:
            interp = 'Yuqori intensivlikda pressing'
        elif ppda < 16:
            interp = "O'rtacha pressing"
        else:
            interp = 'Past pressing — chuqur himoya'

        return {
            'ppda': ppda,
            'opponent_passes': opp_passes,
            'defensive_actions': turnovers_won,
            'interpretation': interp,
        }

    def _compute_transitions(self, ball_df, players_df, fps, team, opp_team, court_length):
        """
        Transition tahlili — to'p almashgandan keyingi 5-8 soniyada nima bo'ldi.
        - To'p qancha tez hujumga o'tdi?
        - Raqib qancha tartibsiz holat?
        """
        window_frames = int(self.TRANSITION_WINDOW_S * fps)
        owned = ball_df[ball_df['owner_id'] != -1].copy()
        if owned.empty:
            return self._empty_transitions()

        # To'p almashgan lahzalarni topish
        owned['prev_team'] = owned['owner_team'].shift(1)
        turnovers = owned[owned['owner_team'] != owned['prev_team']].copy()

        # Bu jamoaga o'tgan transitions (counter-attack imkoniyatlari)
        won_ball = turnovers[turnovers['owner_team'] == team]
        # Bu jamoadan ketgan transitions (raqibning counter-attack)
        lost_ball = turnovers[turnovers['owner_team'] == opp_team]

        attack_transitions = []
        for _, row in won_ball.iterrows():
            start_frame = int(row['frame'])
            end_frame = start_frame + window_frames

            # Window ichidagi o'yinchilar
            window_players = players_df[
                (players_df['frame'] >= start_frame) &
                (players_df['frame'] <= end_frame) &
                (players_df['team'] == team)
            ]
            if window_players.empty:
                continue

            # Qancha masofa oldinga bosdi?
            start_y = window_players[window_players['frame'] == start_frame]['y'].mean()
            end_y = window_players[window_players['frame'] <= end_frame]['y'].max()
            advance_m = (end_y - start_y) if pd.notna(start_y) and pd.notna(end_y) else 0

            # Max tezlik transition paytida
            max_speed = window_players['speed_kmh'].max() if 'speed_kmh' in window_players.columns else 0

            attack_transitions.append({
                'frame': start_frame,
                'time_s': round(start_frame / fps, 1),
                'advance_m': round(float(advance_m), 1),
                'max_speed_kmh': round(float(max_speed), 1),
                'reached_danger_zone': float(advance_m) > court_length * (1 - self.DANGER_ZONE_Y_PCT),
            })

        # Defence transitions (raqib to'pni olganda)
        defence_transitions = []
        for _, row in lost_ball.iterrows():
            start_frame = int(row['frame'])
            end_frame = start_frame + window_frames

            window_opp = players_df[
                (players_df['frame'] >= start_frame) &
                (players_df['frame'] <= end_frame) &
                (players_df['team'] == opp_team)
            ]
            if window_opp.empty:
                continue

            start_y = window_opp[window_opp['frame'] == start_frame]['y'].mean()
            end_y = window_opp[window_opp['frame'] <= end_frame]['y'].max()
            opp_advance = (end_y - start_y) if pd.notna(start_y) and pd.notna(end_y) else 0

            defence_transitions.append({
                'frame': start_frame,
                'opp_advance_m': round(float(opp_advance), 1),
            })

        # O'rtacha statistikalar
        avg_attack_advance = np.mean([t['advance_m'] for t in attack_transitions]) if attack_transitions else 0
        danger_reached = sum(1 for t in attack_transitions if t.get('reached_danger_zone'))
        avg_def_conceded = np.mean([t['opp_advance_m'] for t in defence_transitions]) if defence_transitions else 0

        return {
            'attack_transitions_count': len(attack_transitions),
            'avg_attack_advance_m': round(float(avg_attack_advance), 1),
            'danger_zone_reached': danger_reached,
            'danger_zone_pct': round(100 * danger_reached / max(len(attack_transitions), 1), 1),
            'defence_transitions_count': len(defence_transitions),
            'avg_conceded_advance_m': round(float(avg_def_conceded), 1),
            'top_transitions': sorted(attack_transitions, key=lambda x: -x['advance_m'])[:5],
        }

    def _compute_progressive_passes(self, ball_df, players_df, fps, team, court_length):
        """
        Progressive pass — maydonni kamida 5m oldinga olib borgan pas.
        Kim ko'p progressive pas bergan? Kim qabul qilgan?
        """
        owned = ball_df[ball_df['owner_team'] == team].copy()
        if owned.empty:
            return {'count': 0, 'top_progressors': []}

        valid = players_df.dropna(subset=['x', 'y'])
        segments = []
        prev_owner = -1
        prev_frame = -1
        prev_y = 0

        for _, row in owned.iterrows():
            curr_owner = int(row['owner_id'])
            frame = int(row['frame'])

            if curr_owner != prev_owner and prev_owner != -1:
                # Yangi o'yinchiga o'tdi — bu pas
                curr_row = valid[(valid['player_id'] == curr_owner) & (valid['frame'] == frame)]
                if not curr_row.empty:
                    curr_y = float(curr_row.iloc[0]['y'])
                    advance = curr_y - prev_y
                    if advance >= self.PROGRESSIVE_PASS_MIN_M:
                        segments.append({
                            'from_player': prev_owner,
                            'to_player': curr_owner,
                            'advance_m': round(advance, 1),
                            'frame': frame,
                        })

            # Hozirgi pozitsiyani saqlash
            pos_row = valid[(valid['player_id'] == curr_owner) & (valid['frame'] == frame)]
            if not pos_row.empty:
                prev_y = float(pos_row.iloc[0]['y'])

            prev_owner = curr_owner
            prev_frame = frame

        # Top progressors
        from_counts = {}
        for s in segments:
            pid = s['from_player']
            from_counts[pid] = from_counts.get(pid, 0) + 1

        top_progressors = sorted(from_counts.items(), key=lambda x: -x[1])[:5]

        return {
            'count': len(segments),
            'avg_advance_m': round(np.mean([s['advance_m'] for s in segments]), 1) if segments else 0,
            'top_progressors': [
                {'player_id': int(pid), 'count': cnt}
                for pid, cnt in top_progressors
            ],
        }

    def _compute_recovery_map(self, ball_df, players_df, team, court_length):
        """
        Recovery Map — to'p QAYERDA qaytarib olingan.
        Zonalarga ajratish: himoya / o'rta / hujum.
        """
        owned = ball_df[ball_df['owner_id'] != -1].copy()
        if owned.empty:
            return {'zones': {}, 'total_recoveries': 0}

        owned['prev_team'] = owned['owner_team'].shift(1)
        recoveries = owned[
            (owned['owner_team'] == team) &
            (owned['prev_team'] != team) &
            (owned['prev_team'] > 0)
        ]

        valid = players_df.dropna(subset=['x', 'y'])
        zones = {'defensive': 0, 'middle': 0, 'attacking': 0}
        recovery_positions = []

        for _, row in recoveries.iterrows():
            pid = int(row['owner_id'])
            frame = int(row['frame'])
            pos = valid[(valid['player_id'] == pid) & (valid['frame'] == frame)]
            if pos.empty:
                continue
            y = float(pos.iloc[0]['y'])
            x = float(pos.iloc[0]['x'])
            recovery_positions.append({'x': round(x, 1), 'y': round(y, 1)})

            if y < court_length / 3:
                zones['defensive'] += 1
            elif y < 2 * court_length / 3:
                zones['middle'] += 1
            else:
                zones['attacking'] += 1

        total = sum(zones.values()) or 1
        zone_pct = {k: round(100 * v / total, 1) for k, v in zones.items()}

        return {
            'total_recoveries': sum(zones.values()),
            'zone_distribution': zones,
            'zone_pct': zone_pct,
            'top_positions': recovery_positions[:20],  # Heatmap uchun
        }

    def _compute_danger_zones(self, ball_df, players_df, team, opp_team, court_length):
        """
        Raqibning xavfli hududlari — QAYERDA zaif?
        Raqib to'pni ko'p yo'qotadigan zona va vaqtni hisoblash.
        """
        owned = ball_df[ball_df['owner_id'] != -1].copy()
        if owned.empty:
            return {'zones': {}, 'weak_side': None}

        owned['prev_team'] = owned['owner_team'].shift(1)
        # Raqib to'pni yo'qotgan joylar
        opp_losses = owned[
            (owned['owner_team'] == team) &  # biz oldik
            (owned['prev_team'] == opp_team)  # raqib yo'qotdi
        ]

        valid = players_df.dropna(subset=['x', 'y'])
        left_losses = 0
        right_losses = 0
        court_width_x = 23.32  # x-axis

        loss_positions = []
        for _, row in opp_losses.iterrows():
            frame = int(row['frame'])
            # Raqib o'yinchisining pozitsiyasi (oldingi frame)
            prev_frame = max(frame - 1, 0)
            opp_players = valid[(valid['frame'] == prev_frame) & (valid['team'] == opp_team)]
            if opp_players.empty:
                continue

            # O'rtacha pozitsiya
            avg_x = opp_players['x'].mean()
            avg_y = opp_players['y'].mean()
            loss_positions.append({'x': round(float(avg_x), 1), 'y': round(float(avg_y), 1)})

            if avg_x < court_width_x / 2:
                left_losses += 1
            else:
                right_losses += 1

        weak_side = None
        if left_losses + right_losses > 3:
            if left_losses > right_losses * 1.5:
                weak_side = 'left'
            elif right_losses > left_losses * 1.5:
                weak_side = 'right'

        # Y-axis bo'yicha hududlar
        zones = {'defensive_third': 0, 'middle_third': 0, 'attacking_third': 0}
        for p in loss_positions:
            if p['y'] < court_length / 3:
                zones['defensive_third'] += 1
            elif p['y'] < 2 * court_length / 3:
                zones['middle_third'] += 1
            else:
                zones['attacking_third'] += 1

        return {
            'opponent_ball_losses': len(loss_positions),
            'zone_distribution': zones,
            'weak_side': weak_side,
            'weak_side_detail': f"Raqibning {'chap' if weak_side == 'left' else 'o`ng'} qanoti zaif ({left_losses} vs {right_losses} to'p yo'qotish)" if weak_side else "Zaif qanot aniqlanmadi",
            'loss_positions': loss_positions[:15],
        }

    def _compute_vulnerability(self, ball_df, players_df, fps, opp_team):
        """
        KIM pressga dosh berolmaydi? Raqib o'yinchilarini individual tahlil.
        Eng ko'p turnover qilgan o'yinchilar.
        """
        owned = ball_df[ball_df['owner_id'] != -1].copy()
        if owned.empty:
            return {'vulnerable_players': []}

        owned['prev_owner'] = owned['owner_id'].shift(1)
        owned['prev_team'] = owned['owner_team'].shift(1)

        # Raqib o'yinchilarining turnover'lari
        turnovers_by_player = {}
        for _, row in owned.iterrows():
            if int(row['prev_team']) == opp_team and int(row['owner_team']) != opp_team:
                pid = int(row['prev_owner'])
                if pid > 0:
                    turnovers_by_player[pid] = turnovers_by_player.get(pid, 0) + 1

        # Raqib o'yinchilarining egalik vaqti
        opp_owned = owned[owned['owner_team'] == opp_team]
        time_by_player = opp_owned.groupby('owner_id').size()

        vulnerable = []
        for pid, turnover_count in turnovers_by_player.items():
            if turnover_count < self.VULNERABILITY_TURNOVER_MIN:
                continue
            time_frames = time_by_player.get(pid, 1)
            time_s = time_frames / fps
            turnover_rate = round(turnover_count / max(time_s / 60, 0.1), 1)  # per minute

            vulnerable.append({
                'player_id': int(pid),
                'turnovers': turnover_count,
                'possession_time_s': round(float(time_s), 1),
                'turnover_rate_per_min': turnover_rate,
            })

        vulnerable.sort(key=lambda x: -x['turnovers'])

        return {
            'vulnerable_players': vulnerable[:5],
            'most_vulnerable': vulnerable[0] if vulnerable else None,
            'recommendation': self._vulnerability_recommendation(vulnerable),
        }

    def _vulnerability_recommendation(self, vulnerable):
        if not vulnerable:
            return "Raqib o'yinchilari orasida aniq zaif nuqta topilmadi"
        top = vulnerable[0]
        return (
            f"#{top['player_id']} raqib o'yinchisiga pressing qiling — "
            f"{top['turnovers']} marta to'p yo'qotdi "
            f"({top['turnover_rate_per_min']} /min)"
        )

    def _compute_fatigue_timeline(self, players_df, fps, team):
        """
        QACHON charchaydi? Sprint intensivligini vaqt bo'yicha ko'rish.
        5-minutlik oynalarda sprint sonini hisoblash.
        """
        team_data = players_df[players_df['team'] == team]
        if team_data.empty or 'speed_kmh' not in team_data.columns:
            return {'windows': [], 'fatigue_detected': False}

        max_frame = int(team_data['frame'].max())
        window_frames = int(self.FATIGUE_WINDOW_MINUTES * 60 * fps)

        windows = []
        frame = 0
        while frame < max_frame:
            end = min(frame + window_frames, max_frame)
            window_data = team_data[(team_data['frame'] >= frame) & (team_data['frame'] < end)]

            sprint_count = (window_data['speed_kmh'] > 20).sum() if not window_data.empty else 0
            avg_speed = float(window_data['speed_kmh'].mean()) if not window_data.empty else 0
            max_speed = float(window_data['speed_kmh'].max()) if not window_data.empty else 0

            windows.append({
                'minute_start': round(frame / fps / 60, 1),
                'minute_end': round(end / fps / 60, 1),
                'sprint_frames': int(sprint_count),
                'avg_speed_kmh': round(avg_speed, 1),
                'max_speed_kmh': round(max_speed, 1),
            })
            frame = end

        # Fatigue detection — oxirgi 2 window birinchi 2 ga nisbatan 40%+ kam sprint
        fatigue_detected = False
        fatigue_minute = None
        if len(windows) >= 4:
            first_avg = np.mean([w['sprint_frames'] for w in windows[:2]])
            last_avg = np.mean([w['sprint_frames'] for w in windows[-2:]])
            if first_avg > 0 and last_avg < first_avg * 0.6:
                fatigue_detected = True
                # Qaysi momentdan boshlab tushdi?
                for i in range(2, len(windows)):
                    recent = np.mean([w['sprint_frames'] for w in windows[i-1:i+1]])
                    if recent < first_avg * 0.6:
                        fatigue_minute = windows[i]['minute_start']
                        break

        return {
            'windows': windows,
            'fatigue_detected': fatigue_detected,
            'fatigue_start_minute': fatigue_minute,
            'interpretation': (
                f"Charchoq {fatigue_minute:.0f}-daqiqadan boshlab aniqlanadi — almashtirish tavsiya etiladi"
                if fatigue_detected
                else "Charchoq alomatlari aniqlanmadi"
            ),
        }

    def _empty_transitions(self):
        return {
            'attack_transitions_count': 0,
            'avg_attack_advance_m': 0,
            'danger_zone_reached': 0,
            'danger_zone_pct': 0,
            'defence_transitions_count': 0,
            'avg_conceded_advance_m': 0,
            'top_transitions': [],
        }

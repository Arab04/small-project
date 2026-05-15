"""Team Comparator - kuchli/zaif tomonlarni avtomatik aniqlash"""


class TeamComparator:
    THRESHOLDS = {
        'possession_dominant': 60,
        'sprint_advantage': 5,
        'pressure_high': 4.5,
        'pressure_low': 8.0,
        'pass_acc_good': 80,
        'distance_advantage': 500,
    }

    def compare(self, possession, speed, heatmap, passes, pressing, teams):
        if len(teams) < 2:
            return {'narrative': 'Tahlil uchun ikki kamanda kerak'}

        t1, t2 = teams[0], teams[1]
        result = {
            f'team_{t1}': {'strengths': [], 'weaknesses': []},
            f'team_{t2}': {'strengths': [], 'weaknesses': []},
        }
        self._compare_possession(possession, t1, t2, result)
        self._compare_speed(speed, t1, t2, result)
        self._compare_compactness(heatmap, t1, t2, result)
        self._compare_passing(passes, t1, t2, result)
        self._compare_pressing(pressing, t1, t2, result)
        result['narrative'] = self._build_narrative(result, t1, t2)
        return result

    def _compare_possession(self, possession, t1, t2, result):
        p1 = possession.get(f'team_{t1}', {})
        p2 = possession.get(f'team_{t2}', {})
        pct1, pct2 = p1.get('possession_pct', 0), p2.get('possession_pct', 0)
        if pct1 >= self.THRESHOLDS['possession_dominant']:
            result[f'team_{t1}']['strengths'].append(f"Dominant possession ({pct1}%)")
            result[f'team_{t2}']['weaknesses'].append(f"To'pni saqlay olmaydi ({pct2}%)")
        elif pct2 >= self.THRESHOLDS['possession_dominant']:
            result[f'team_{t2}']['strengths'].append(f"Dominant possession ({pct2}%)")
            result[f'team_{t1}']['weaknesses'].append(f"To'pni saqlay olmaydi ({pct1}%)")

        a1 = p1.get('attacking_third_possession_s', 0)
        a2 = p2.get('attacking_third_possession_s', 0)
        if a1 > a2 * 1.5 and a1 > 5:
            result[f'team_{t1}']['strengths'].append(f"Hujum maydonida bosim ({a1}s)")
        elif a2 > a1 * 1.5 and a2 > 5:
            result[f'team_{t2}']['strengths'].append(f"Hujum maydonida bosim ({a2}s)")

    def _compare_speed(self, speed, t1, t2, result):
        s1 = speed.get(f'team_{t1}', {})
        s2 = speed.get(f'team_{t2}', {})
        sprints1, sprints2 = s1.get('total_sprints', 0), s2.get('total_sprints', 0)
        if sprints1 - sprints2 >= self.THRESHOLDS['sprint_advantage']:
            result[f'team_{t1}']['strengths'].append(
                f"Yuqori intensivlik ({sprints1} vs {sprints2} sprint)")
        elif sprints2 - sprints1 >= self.THRESHOLDS['sprint_advantage']:
            result[f'team_{t2}']['strengths'].append(
                f"Yuqori intensivlik ({sprints2} vs {sprints1} sprint)")

        d1, d2 = s1.get('total_distance_m', 0), s2.get('total_distance_m', 0)
        if d1 - d2 >= self.THRESHOLDS['distance_advantage']:
            result[f'team_{t1}']['strengths'].append(f"Ko'p yuguradi ({d1:.0f}m vs {d2:.0f}m)")
        elif d2 - d1 >= self.THRESHOLDS['distance_advantage']:
            result[f'team_{t2}']['strengths'].append(f"Ko'p yuguradi ({d2:.0f}m vs {d1:.0f}m)")

        max1, max2 = s1.get('max_speed_kmh', 0), s2.get('max_speed_kmh', 0)
        if max1 - max2 >= 3:
            result[f'team_{t1}']['strengths'].append(f"Tezkor oyinchilar (max {max1} km/h)")
        elif max2 - max1 >= 3:
            result[f'team_{t2}']['strengths'].append(f"Tezkor oyinchilar (max {max2} km/h)")

    def _compare_compactness(self, heatmap, t1, t2, result):
        h1, h2 = heatmap.get(f'team_{t1}', {}), heatmap.get(f'team_{t2}', {})
        s1, s2 = h1.get('spread_m', 0), h2.get('spread_m', 0)
        if s1 and s2:
            if s2 - s1 > 3:
                result[f'team_{t1}']['strengths'].append(f"Kompakt struktura (spread {s1:.1f}m)")
                result[f'team_{t2}']['weaknesses'].append(f"Tarqoq formatsiya (spread {s2:.1f}m)")
            elif s1 - s2 > 3:
                result[f'team_{t2}']['strengths'].append(f"Kompakt struktura (spread {s2:.1f}m)")
                result[f'team_{t1}']['weaknesses'].append(f"Tarqoq formatsiya (spread {s1:.1f}m)")

    def _compare_passing(self, passes, t1, t2, result):
        p1, p2 = passes.get(f'team_{t1}', {}), passes.get(f'team_{t2}', {})
        acc1, acc2 = p1.get('pass_accuracy_pct', 0), p2.get('pass_accuracy_pct', 0)
        n1, n2 = p1.get('total_passes', 0), p2.get('total_passes', 0)
        if n1 >= 5 and acc1 >= self.THRESHOLDS['pass_acc_good']:
            result[f'team_{t1}']['strengths'].append(f"Aniq peredacha ({acc1}%, {n1} ta)")
        if n2 >= 5 and acc2 >= self.THRESHOLDS['pass_acc_good']:
            result[f'team_{t2}']['strengths'].append(f"Aniq peredacha ({acc2}%, {n2} ta)")
        if n1 >= 5 and acc1 < 60:
            result[f'team_{t1}']['weaknesses'].append(f"Past peredacha aniqligi ({acc1}%)")
        if n2 >= 5 and acc2 < 60:
            result[f'team_{t2}']['weaknesses'].append(f"Past peredacha aniqligi ({acc2}%)")

    def _compare_pressing(self, pressing, t1, t2, result):
        p1, p2 = pressing.get(f'team_{t1}', {}), pressing.get(f'team_{t2}', {})
        d1, d2 = p1.get('avg_pressure_distance_m'), p2.get('avg_pressure_distance_m')

        if d1 is not None and d1 < self.THRESHOLDS['pressure_high']:
            result[f'team_{t1}']['strengths'].append(f"Agressiv pressing (o'rt. {d1}m)")
        elif d1 is not None and d1 > self.THRESHOLDS['pressure_low']:
            result[f'team_{t1}']['weaknesses'].append(f"Sust himoya (o'rt. {d1}m)")

        if d2 is not None and d2 < self.THRESHOLDS['pressure_high']:
            result[f'team_{t2}']['strengths'].append(f"Agressiv pressing (o'rt. {d2}m)")
        elif d2 is not None and d2 > self.THRESHOLDS['pressure_low']:
            result[f'team_{t2}']['weaknesses'].append(f"Sust himoya (o'rt. {d2}m)")

        z1 = p1.get('pressing_zone_pct', {})
        z2 = p2.get('pressing_zone_pct', {})
        if z1.get('high_press_zone', 0) > 40:
            result[f'team_{t1}']['strengths'].append(
                f"Yuqori liniyadan pressing ({z1['high_press_zone']}%)")
        if z2.get('high_press_zone', 0) > 40:
            result[f'team_{t2}']['strengths'].append(
                f"Yuqori liniyadan pressing ({z2['high_press_zone']}%)")

    def _build_narrative(self, result, t1, t2):
        s1 = result[f'team_{t1}']['strengths']
        w1 = result[f'team_{t1}']['weaknesses']
        s2 = result[f'team_{t2}']['strengths']
        w2 = result[f'team_{t2}']['weaknesses']
        parts = []
        if s1: parts.append(f"Team {t1} kuchli tomonlari: " + "; ".join(s1) + ".")
        if w1: parts.append(f"Team {t1} zaif tomonlari: " + "; ".join(w1) + ".")
        if s2: parts.append(f"Team {t2} kuchli tomonlari: " + "; ".join(s2) + ".")
        if w2: parts.append(f"Team {t2} zaif tomonlari: " + "; ".join(w2) + ".")
        if not parts:
            parts.append("Ikki kamanda balanslangan o'ynadi.")
        return " ".join(parts)

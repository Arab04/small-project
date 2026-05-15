"""Video Overlay - draws analytics panel on video frames"""
import cv2
import numpy as np


class AnalyticsOverlay:
    def __init__(self, panel_width_pct=0.20, panel_alpha=0.55):
        self.panel_width_pct = panel_width_pct
        self.alpha = panel_alpha

    def draw(self, frames, results):
        possession = results.get('possession', {})
        speed = results.get('speed', {})
        pressing = results.get('pressing', {})
        passes = results.get('passes', {})
        teams = results.get('metadata', {}).get('teams', [1, 2])

        out = []
        for frame in frames:
            f = self._draw_side_panel(frame, possession, speed, pressing,
                                       passes, teams)
            out.append(f)
        return out

    def _draw_side_panel(self, frame, possession, speed, pressing, passes, teams):
        h, w = frame.shape[:2]
        panel_w = int(w * self.panel_width_pct)
        x0 = w - panel_w - 10
        y0 = 10
        panel_h = int(h * 0.55)

        overlay = frame.copy()
        cv2.rectangle(overlay, (x0, y0), (x0 + panel_w, y0 + panel_h),
                      (20, 20, 30), -1)
        cv2.addWeighted(overlay, self.alpha, frame, 1 - self.alpha, 0, frame)
        cv2.rectangle(frame, (x0, y0), (x0 + panel_w, y0 + panel_h),
                      (200, 200, 200), 1)

        fs_title = max(0.45, w / 2400.0)
        fs = max(0.38, w / 2800.0)
        line_h = int(20 * fs / 0.4)
        y = y0 + line_h + 5

        cv2.putText(frame, "MATCH ANALYTICS", (x0 + 10, y),
                    cv2.FONT_HERSHEY_SIMPLEX, fs_title, (255, 255, 255), 1)
        y += line_h + 4

        for team in teams:
            color = (255, 220, 100) if team == 1 else (100, 180, 255)
            p = possession.get(f'team_{team}', {})
            s = speed.get(f'team_{team}', {})
            ps = passes.get(f'team_{team}', {})
            pr = pressing.get(f'team_{team}', {})

            cv2.putText(frame, f"TEAM {team}", (x0 + 10, y),
                        cv2.FONT_HERSHEY_SIMPLEX, fs_title * 0.9, color, 1)
            y += line_h

            press_d = pr.get('avg_pressure_distance_m')
            press_str = f"{press_d:.1f}m" if press_d is not None else "N/A"

            stats = [
                f"Possession:  {p.get('possession_pct', 0):.1f}%",
                f"Sprintlar:   {s.get('total_sprints', 0)}",
                f"Max km/h:    {s.get('max_speed_kmh', 0):.1f}",
                f"Distance:    {s.get('total_distance_m', 0):.0f} m",
                f"Passes:      {ps.get('total_passes', 0)} ({ps.get('pass_accuracy_pct', 0):.0f}%)",
                f"Press dist:  {press_str}",
            ]
            for line in stats:
                cv2.putText(frame, line, (x0 + 14, y),
                            cv2.FONT_HERSHEY_SIMPLEX, fs, (220, 220, 220), 1)
                y += line_h - 2
            y += 6

        return frame

"""Reporter - JSON + HTML"""
import base64
import json
import os
from datetime import datetime


def _to_jsonable(obj):
    import numpy as np
    if isinstance(obj, dict):
        return {str(k): _to_jsonable(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [_to_jsonable(v) for v in obj]
    if isinstance(obj, (np.integer,)):
        return int(obj)
    if isinstance(obj, (np.floating,)):
        return float(obj)
    if isinstance(obj, np.ndarray):
        return obj.tolist()
    return obj


class Reporter:
    def __init__(self, output_dir='output_videos/analytics'):
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)

    def write_json(self, data, filename='analytics.json'):
        path = os.path.join(self.output_dir, filename)
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(_to_jsonable(data), f, indent=2, ensure_ascii=False)
        return path

    def write_html(self, data, filename='dashboard.html'):
        path = os.path.join(self.output_dir, filename)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(self._build_html(data))
        return path

    def _img_to_b64(self, image_path):
        if not os.path.exists(image_path):
            return None
        with open(image_path, 'rb') as f:
            return base64.b64encode(f.read()).decode('utf-8')

    def _build_html(self, data):
        meta = data.get('metadata', {})
        teams = meta.get('teams', [1, 2])
        possession = data.get('possession', {})
        speed = data.get('speed', {})
        heatmap = data.get('heatmap', {})
        passes = data.get('passes', {})
        pressing = data.get('pressing', {})
        comparison = data.get('comparison', {})

        heatmap_imgs, passnet_imgs = {}, {}
        for t in teams:
            heatmap_imgs[t] = self._img_to_b64(
                os.path.join(self.output_dir, f'heatmap_team_{t}.png'))
            passnet_imgs[t] = self._img_to_b64(
                os.path.join(self.output_dir, f'pass_network_team_{t}.png'))

        team_cards = ''.join(
            self._team_card(t, possession, speed, heatmap, passes, pressing,
                            comparison, heatmap_imgs.get(t), passnet_imgs.get(t))
            for t in teams
        )
        narrative = comparison.get('narrative', '')
        narrative_html = (f'<div class="narrative"><b>Xulosa:</b> {narrative}</div>'
                          if narrative else '')

        return f"""<!DOCTYPE html>
<html lang="uz"><head><meta charset="UTF-8"><title>Match Analytics</title>
<style>
*{{box-sizing:border-box;margin:0;padding:0}}
body{{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#0f1729;color:#e6e9ef;padding:30px;line-height:1.5}}
.container{{max-width:1400px;margin:0 auto}}
h1{{font-size:28px;margin-bottom:8px}}
.subtitle{{color:#8b9bb4;font-size:14px;margin-bottom:24px}}
.meta-bar{{background:#1a2540;padding:12px 20px;border-radius:8px;display:flex;gap:30px;margin-bottom:24px;flex-wrap:wrap;font-size:13px}}
.meta-bar span{{color:#8b9bb4}}.meta-bar b{{color:#fff}}
.narrative{{background:linear-gradient(135deg,#1e3a5f,#2a4a7a);padding:18px 22px;border-radius:8px;margin-bottom:24px;border-left:4px solid #4a90e2;font-size:14px}}
.grid{{display:grid;grid-template-columns:1fr 1fr;gap:20px}}
.card{{background:#1a2540;border-radius:10px;padding:22px;border:1px solid #2a3a5a}}
.card h2{{font-size:18px;margin-bottom:14px;padding-bottom:8px;border-bottom:1px solid #2a3a5a}}
.stat-row{{display:flex;justify-content:space-between;padding:6px 0;font-size:13px;border-bottom:1px dashed #243353}}
.stat-row:last-child{{border-bottom:none}}.stat-row .label{{color:#8b9bb4}}.stat-row .value{{color:#fff;font-weight:500}}
.pill{{display:inline-block;padding:4px 10px;border-radius:12px;font-size:11px;margin:2px 4px 2px 0}}
.pill.strength{{background:#1e5128;color:#7fdc8a}}
.pill.weakness{{background:#5c1f1f;color:#ff8888}}
.section-title{{font-size:13px;color:#8b9bb4;text-transform:uppercase;margin:14px 0 6px;letter-spacing:.5px}}
.speed-bar{{display:flex;height:18px;border-radius:4px;overflow:hidden;margin-top:6px;background:#0f1729}}
.speed-bar div{{display:flex;align-items:center;justify-content:center;color:white;font-size:10px}}
.speed-bar .walk{{background:#4a5c70}}.speed-bar .jog{{background:#5c8a4f}}
.speed-bar .run{{background:#c79a3a}}.speed-bar .sprint{{background:#d04545}}
.img-block{{margin-top:12px}}.img-block img{{width:100%;border-radius:6px}}
.footer{{text-align:center;color:#5b6a8a;font-size:12px;margin-top:30px}}
</style></head><body><div class="container">
<h1>⚽ Match Analytics Dashboard</h1>
<div class="subtitle">Hisobot: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</div>
<div class="meta-bar">
  <span>Davomiyligi: <b>{meta.get('duration_s', 0):.1f}s</b></span>
  <span>Freym: <b>{meta.get('total_frames', 0)}</b></span>
  <span>FPS: <b>{meta.get('fps', 24)}</b></span>
  <span>Maydon: <b>{meta.get('court_length', '?')} × {meta.get('court_width', '?')} m</b></span>
</div>
{narrative_html}
<div class="grid">{team_cards}</div>
<div class="footer">Football Analysis Enhanced</div>
</div></body></html>"""

    def _team_card(self, team, possession, speed, heatmap, passes, pressing,
                   comparison, heatmap_b64, passnet_b64):
        p = possession.get(f'team_{team}', {})
        s = speed.get(f'team_{team}', {})
        ps = passes.get(f'team_{team}', {})
        pr = pressing.get(f'team_{team}', {})
        comp = comparison.get(f'team_{team}', {})

        bands = s.get('speed_band_pct', {})
        speed_bar = (
            f'<div class="speed-bar">'
            f'<div class="walk" style="width:{bands.get("walking", 0)}%">{bands.get("walking", 0)}%</div>'
            f'<div class="jog" style="width:{bands.get("jogging", 0)}%">{bands.get("jogging", 0)}%</div>'
            f'<div class="run" style="width:{bands.get("running", 0)}%">{bands.get("running", 0)}%</div>'
            f'<div class="sprint" style="width:{bands.get("sprinting", 0)}%">{bands.get("sprinting", 0)}%</div>'
            f'</div>'
        )

        strengths_html = ''.join(f'<span class="pill strength">{x}</span>'
                                  for x in comp.get('strengths', []))
        weaknesses_html = ''.join(f'<span class="pill weakness">{x}</span>'
                                   for x in comp.get('weaknesses', []))
        no_str = '<span style="color:#5b6a8a;font-size:12px">Aniqlanmadi</span>'

        heatmap_html = (f'<div class="img-block"><img src="data:image/png;base64,{heatmap_b64}"></div>'
                        if heatmap_b64 else '')
        passnet_html = (f'<div class="img-block"><img src="data:image/png;base64,{passnet_b64}"></div>'
                        if passnet_b64 else '')

        top_passer = ps.get('top_passer')
        top_passer_str = (f"#{top_passer['player_id']} ({top_passer['count']} ta)"
                          if top_passer else 'N/A')
        zone = pr.get('pressing_zone_pct', {})
        press_dist = pr.get('avg_pressure_distance_m')
        press_dist_str = f"{press_dist}" if press_dist is not None else "N/A"

        return f'''<div class="card">
<h2>Team {team}</h2>
<div class="section-title">Kuchli tomonlari</div>
<div>{strengths_html or no_str}</div>
<div class="section-title">Zaif tomonlari</div>
<div>{weaknesses_html or no_str}</div>
<div class="section-title">Possession</div>
<div class="stat-row"><span class="label">Ball control</span><span class="value">{p.get('possession_pct', 0)}% ({p.get('possession_seconds', 0)}s)</span></div>
<div class="stat-row"><span class="label">Egalik soni</span><span class="value">{p.get('num_possessions', 0)}</span></div>
<div class="stat-row"><span class="label">O'rt. davomiyligi</span><span class="value">{p.get('avg_possession_duration_s', 0)}s</span></div>
<div class="stat-row"><span class="label">Hujum uchligida</span><span class="value">{p.get('attacking_third_possession_s', 0)}s</span></div>
<div class="section-title">Tezlik & masofa</div>
<div class="stat-row"><span class="label">Max tezlik</span><span class="value">{s.get('max_speed_kmh', 0)} km/h</span></div>
<div class="stat-row"><span class="label">O'rt. tezlik</span><span class="value">{s.get('avg_speed_kmh', 0)} km/h</span></div>
<div class="stat-row"><span class="label">Jami masofa</span><span class="value">{s.get('total_distance_m', 0):.0f} m</span></div>
<div class="stat-row"><span class="label">Sprint soni</span><span class="value">{s.get('total_sprints', 0)}</span></div>
{speed_bar}
<div class="section-title">Pass tarmog'i</div>
<div class="stat-row"><span class="label">Jami passes</span><span class="value">{ps.get('total_passes', 0)}</span></div>
<div class="stat-row"><span class="label">Aniqlik</span><span class="value">{ps.get('pass_accuracy_pct', 0)}%</span></div>
<div class="stat-row"><span class="label">Eng faol uzatuvchi</span><span class="value">{top_passer_str}</span></div>
{passnet_html}
<div class="section-title">Pressing</div>
<div class="stat-row"><span class="label">O'rt. masofasi</span><span class="value">{press_dist_str} m</span></div>
<div class="stat-row"><span class="label">High press %</span><span class="value">{pr.get('high_press_pct', 0)}%</span></div>
<div class="stat-row"><span class="label">Press zonasi</span><span class="value">L:{zone.get('low_block', 0)}% / M:{zone.get('mid_press', 0)}% / H:{zone.get('high_press_zone', 0)}%</span></div>
<div class="section-title">Heatmap</div>
{heatmap_html}
</div>'''

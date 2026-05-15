"""
Football Analysis Pipeline (Enhanced v2)
=========================================
- Robust YOLO + ByteTrack detection/tracking
- Camera movement compensation
- Configurable view transformer (calibration.json)
- Speed/distance with smoothing & outlier rejection
- KMeans team color assignment
- Ball ownership detection
- Comprehensive analytics:
    * Track filtering (50 IDs -> 13)
    * Team direction detection
    * Possession, sprints, heatmap, pass network, pressing
    * Adaptive thresholds (pro/youtube/amateur presets)
    * JSON + interactive HTML dashboard
    * Video overlay panel

Konfiguratsiya:
    main.py o'zgartirmasdan, command-line argumentlar orqali sozlash mumkin:
        python main.py --video input_videos/match.mp4 --preset amateur

Yoki main.py ichida CONFIG ni o'zgartiring (quyida).
"""
import argparse
import os
import time

import cv2
import numpy as np

from utils import read_video, save_video
from trackers import Tracker
from team_assigner import TeamAssigner
from player_ball_assigner import PlayerBallAssigner
from camera_movement_estimator import CameraMovementEstimator
from view_transformer import ViewTransformer
from speed_and_distance_estimator import SpeedAndDistance_Estimator
from team_analytics import MatchAnalyzer, AnalyticsOverlay


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def main(video_path='input_videos/08fd33_4.mp4',
         model_path='models/best.pt',
         preset='youtube',
         output_dir='output_videos'):
    t0 = time.time()

    # ---------- 0. GPU tekshiruv ----------
    try:
        import torch
        if torch.cuda.is_available():
            log(f"✅ GPU: {torch.cuda.get_device_name(0)} | CUDA {torch.version.cuda}")
        else:
            log("⚠️  GPU TOPILMADI — CPU'da ishlaydi (juda sekin bo'ladi)")
    except ImportError:
        log("⚠️  torch import qilinmadi")

    # ---------- 1. Fayllar borligini tekshirish ----------
    for p in [video_path, model_path]:
        if not os.path.exists(p):
            log(f"❌ Topilmadi: {p}")
            return
        log(f"✅ Topildi: {p} ({os.path.getsize(p) / 1e6:.1f} MB)")

    # Calibration faylini ko'rsatish
    cal_path = 'view_transformer/calibration.json'
    if os.path.exists(cal_path):
        log(f"✅ View calibration: {cal_path}")
    else:
        log(f"⚠️  View calibration topilmadi - default (08fd33_4.mp4) ishlatiladi")
        log(f"   Boshqa video uchun: python tools/calibration_helper.py extract --video {video_path}")

    # ---------- 2. Video o'qish ----------
    log("Video o'qilmoqda...")
    video_frames = read_video(video_path)
    if len(video_frames) == 0:
        log("❌ Video o'qilmadi - codec yo'q yoki fayl buzilgan (apt install ffmpeg)")
        return
    h, w = video_frames[0].shape[:2]
    log(f"✅ {len(video_frames)} freym o'qildi ({w}x{h})")

    # ---------- 3. Tracking ----------
    log("Tracker ishga tushmoqda (YOLO + ByteTrack, tighter params)...")
    tracker = Tracker(model_path)
    tracks = tracker.get_object_tracks(video_frames,
                                        read_from_stub=True,
                                        stub_path='stubs/track_stubs.pkl')
    tracker.add_position_to_tracks(tracks)
    total_detections = sum(len(f) for f in tracks['players'])
    log(f"✅ Tracking tayyor: {len(tracks['players'])} freym, {total_detections} player-detection")

    # ---------- 4. Camera movement ----------
    log("Kamera harakati hisoblanmoqda...")
    cam_estimator = CameraMovementEstimator(video_frames[0])
    cam_movement = cam_estimator.get_camera_movement(
        video_frames, read_from_stub=True,
        stub_path='stubs/camera_movement_stub.pkl')
    cam_estimator.add_adjust_positions_to_tracks(tracks, cam_movement)
    log("✅ Kamera harakati tayyor")

    # ---------- 5. View transform ----------
    log("Perspective transformation...")
    vt = ViewTransformer()
    log(f"   {vt.info()}")
    vt.add_transformed_position_to_tracks(tracks)

    # ---------- 6. Ball interpolation ----------
    tracks["ball"] = tracker.interpolate_ball_positions(tracks["ball"])

    # ---------- 7. Speed & distance (smoothing + outlier filter) ----------
    log("Tezlik va masofa hisoblanmoqda (smoothing + outlier filter)...")
    sd_estimator = SpeedAndDistance_Estimator()
    sd_estimator.add_speed_and_distance_to_tracks(tracks)

    # ---------- 8. Team assignment ----------
    log("Kamandalarni rang bo'yicha ajratish (KMeans n_init=10)...")
    team_assigner = TeamAssigner()
    team_assigner.assign_team_color(video_frames[0], tracks['players'][0])
    for frame_num, player_track in enumerate(tracks['players']):
        for pid, track in player_track.items():
            team = team_assigner.get_player_team(
                video_frames[frame_num], track['bbox'], pid)
            tracks['players'][frame_num][pid]['team'] = team
            tracks['players'][frame_num][pid]['team_color'] = team_assigner.team_colors[team]

    # ---------- 9. Ball acquisition ----------
    log("To'p egaligini hisoblash...")
    player_assigner = PlayerBallAssigner()
    team_ball_control = []
    for frame_num, player_track in enumerate(tracks['players']):
        ball_bbox = tracks['ball'][frame_num][1]['bbox']
        assigned = player_assigner.assign_ball_to_player(player_track, ball_bbox)
        if assigned != -1:
            tracks['players'][frame_num][assigned]['has_ball'] = True
            team_ball_control.append(tracks['players'][frame_num][assigned]['team'])
        else:
            team_ball_control.append(team_ball_control[-1] if team_ball_control else 1)
    team_ball_control = np.array(team_ball_control)

    # ---------- 10. TEAM ANALYTICS ----------
    log("=" * 60)
    log(f"Team Analytics ishga tushmoqda (preset: {preset})...")
    analytics_dir = os.path.join(output_dir, 'analytics')
    analyzer = MatchAnalyzer(
        fps=24,
        court_width=68.0,
        court_length=23.32,
        output_dir=analytics_dir,
        preset=preset,
    )
    analytics_results = analyzer.run(tracks, team_ball_control)
    analyzer.write_reports(analytics_results)
    log("=" * 60)

    # Konsol xulosasi
    narrative = analytics_results.get('comparison', {}).get('narrative', '')
    if narrative:
        log("📊 XULOSA:")
        for line in narrative.split('. '):
            line = line.strip().rstrip('.')
            if line:
                log(f"   • {line}")

    # Track filter natijasi
    fs = analytics_results.get('metadata', {}).get('track_filter_stats', {})
    if fs:
        log(f"📉 Track filter: {fs.get('total_ids_before', 0)} ID -> "
            f"{fs.get('kept_ids', 0)} (T1: {fs.get('team_1_kept', 0)}, "
            f"T2: {fs.get('team_2_kept', 0)})")

    # ---------- 11. Annotatsiyalar chizish ----------
    log("Annotatsiyalar chizilmoqda...")
    out_frames = tracker.draw_annotations(video_frames, tracks, team_ball_control)
    out_frames = cam_estimator.draw_camera_movement(out_frames, cam_movement)
    sd_estimator.draw_speed_and_distance(out_frames, tracks)

    # ---------- 12. Analytics overlay ----------
    log("Analytics overlay qo'shilmoqda...")
    out_frames = AnalyticsOverlay().draw(out_frames, analytics_results)

    # ---------- 13. Saqlash ----------
    os.makedirs(output_dir, exist_ok=True)
    out_path = os.path.join(output_dir, 'output_video.avi')
    save_video(out_frames, out_path)

    if os.path.exists(out_path):
        size_mb = os.path.getsize(out_path) / 1e6
        log(f"✅ Saqlandi: {out_path} ({size_mb:.1f} MB)")
    else:
        log(f"❌ Saqlanmadi: {out_path}")

    log(f"⏱️  Jami vaqt: {time.time() - t0:.1f} sek")
    log("")
    log("📁 Natijalar:")
    log(f"   🎬 Video:      {out_path}")
    log(f"   📊 Dashboard:  {analytics_dir}/dashboard.html")
    log(f"   📋 JSON:       {analytics_dir}/analytics.json")
    log(f"   🔥 Heatmaps:   {analytics_dir}/heatmap_team_*.png")
    log(f"   🔗 Pass nets:  {analytics_dir}/pass_network_team_*.png")


def cli():
    parser = argparse.ArgumentParser(description='Football Analysis Pipeline')
    parser.add_argument('--video', default='input_videos/08fd33_4.mp4',
                         help='Input video path')
    parser.add_argument('--model', default='models/best.pt',
                         help='YOLO model path')
    parser.add_argument('--preset', default='youtube',
                         choices=['pro', 'youtube', 'amateur'],
                         help='Analytics preset (default: youtube)')
    parser.add_argument('--output-dir', default='output_videos',
                         help='Output directory (default: output_videos)')
    args = parser.parse_args()

    main(video_path=args.video, model_path=args.model,
         preset=args.preset, output_dir=args.output_dir)


if __name__ == '__main__':
    cli()

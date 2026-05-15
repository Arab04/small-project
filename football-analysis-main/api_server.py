"""
Football AI — ML Video Analysis API Server
FastAPI wrapper around the real ML pipeline (main.py).
"""
import os
import uuid
import time
import json
import logging
import threading
from typing import Optional, Dict
from pathlib import Path

import boto3
import requests
from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel

# ── Logging ──────────────────────────────────────────────────────────
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("ml-api")

# ── Environment ──────────────────────────────────────────────────────
INTERNAL_TOKEN = os.getenv("INTERNAL_TOKEN", "3fdba615d24b2e065ad5af1309ddec309eac2ba4649e31de")
S3_ENDPOINT = os.getenv("S3_ENDPOINT", "http://31.184.242.73:9100")
S3_ACCESS_KEY = os.getenv("S3_ACCESS_KEY", "minioadmin")
S3_SECRET_KEY = os.getenv("S3_SECRET_KEY", "minioadmin123")
S3_BUCKET = os.getenv("S3_BUCKET", "football-videos")
MODEL_PATH = os.getenv("MODEL_PATH", "models/best.pt")

# ── S3 Client ────────────────────────────────────────────────────────
s3 = boto3.client(
    "s3",
    endpoint_url=S3_ENDPOINT,
    aws_access_key_id=S3_ACCESS_KEY,
    aws_secret_access_key=S3_SECRET_KEY,
    region_name="us-east-1",
)

# ── App ──────────────────────────────────────────────────────────────
app = FastAPI(title="Football AI ML Service", version="2.0")

# In-memory job store
jobs: Dict[str, dict] = {}


# ── Models ───────────────────────────────────────────────────────────
class AnalyzeRequest(BaseModel):
    match_id: str
    video_minio_key: str
    callback_url: Optional[str] = None
    calibration_points: Optional[dict] = None
    is_long_form: Optional[bool] = None


class ResumeRequest(BaseModel):
    match_id: str
    video_minio_key: str
    callback_url: Optional[str] = None
    calibration_points: Optional[dict] = None
    resume_from_checkpoint: Optional[bool] = True


# ── Endpoints ────────────────────────────────────────────────────────
@app.get("/health")
def health():
    import torch
    gpu_ok = torch.cuda.is_available()
    model_ok = os.path.exists(MODEL_PATH)
    return {
        "status": "ok",
        "gpu": gpu_ok,
        "gpu_name": torch.cuda.get_device_name(0) if gpu_ok else None,
        "model_loaded": model_ok,
        "jobs_count": len(jobs),
    }


@app.post("/analyze")
def analyze(req: AnalyzeRequest, authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer ") or authorization[7:] != INTERNAL_TOKEN:
        raise HTTPException(status_code=403, detail="Token noto'g'ri")

    job_id = str(uuid.uuid4())
    jobs[job_id] = {
        "job_id": job_id,
        "match_id": req.match_id,
        "status": "DOWNLOADING",
        "progress": 0,
        "callback_url": req.callback_url,
        "video_minio_key": req.video_minio_key,
        "calibration_points": req.calibration_points,
        "is_long_form": req.is_long_form,
        "created_at": time.time(),
        "result": None,
    }

    log.info(f"[{job_id}] Yangi tahlil: match={req.match_id}, video={req.video_minio_key}")

    t = threading.Thread(target=_process_job, args=(job_id,), daemon=True)
    t.start()

    return {"job_id": job_id, "status": "DOWNLOADING"}


@app.post("/analyze/resume/{job_id}")
def resume_analyze(job_id: str, req: ResumeRequest, authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer ") or authorization[7:] != INTERNAL_TOKEN:
        raise HTTPException(status_code=403, detail="Token noto'g'ri")

    jobs[job_id] = {
        "job_id": job_id,
        "match_id": req.match_id,
        "status": "DOWNLOADING",
        "progress": 0,
        "callback_url": req.callback_url,
        "video_minio_key": req.video_minio_key,
        "calibration_points": req.calibration_points,
        "created_at": time.time(),
        "result": None,
    }

    t = threading.Thread(target=_process_job, args=(job_id,), daemon=True)
    t.start()
    return {"job_id": job_id, "status": "DOWNLOADING"}


@app.get("/jobs/{job_id}")
def get_job_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job topilmadi")
    job = jobs[job_id]
    return {"job_id": job["job_id"], "status": job["status"], "progress": job["progress"]}


@app.get("/jobs/{job_id}/result")
def get_job_result(job_id: str):
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job topilmadi")
    job = jobs[job_id]
    if job["status"] != "COMPLETED":
        raise HTTPException(status_code=425, detail="Job hali tugamagan")
    if job["result"] is None:
        raise HTTPException(status_code=404, detail="Natija topilmadi")
    return job["result"]


@app.delete("/jobs/{job_id}")
def cancel_job(job_id: str, authorization: Optional[str] = Header(None)):
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job topilmadi")
    jobs[job_id]["status"] = "CANCELLED"
    _notify_callback(jobs[job_id].get("callback_url"), job_id, "CANCELLED")
    return {"job_id": job_id, "status": "CANCELLED"}


# ── Processing ───────────────────────────────────────────────────────
def _process_job(job_id: str):
    """Download video from S3, run real ML pipeline, send callback."""
    job = jobs[job_id]
    callback_url = job.get("callback_url")

    try:
        # 1. Download video from MinIO
        video_key = job["video_minio_key"]
        local_video = f"/tmp/{job_id}.mp4"
        log.info(f"[{job_id}] Video yuklanmoqda: {video_key}")

        job["status"] = "DOWNLOADING"
        _notify_callback(callback_url, job_id, "DOWNLOADING")

        try:
            s3.download_file(S3_BUCKET, video_key, local_video)
            file_size = os.path.getsize(local_video) / 1e6
            log.info(f"[{job_id}] Video yuklandi: {local_video} ({file_size:.1f} MB)")
        except Exception as e:
            log.error(f"[{job_id}] S3 yuklab olish xatosi: {e}")
            job["status"] = "FAILED"
            _notify_callback(callback_url, job_id, "FAILED", f"S3 xato: {e}")
            return

        # 2. Run real ML pipeline
        job["status"] = "PROCESSING"
        job["progress"] = 10
        _notify_callback(callback_url, job_id, "PROCESSING")

        result = _run_real_analysis(job_id, local_video, job.get("calibration_points"))

        # 3. Save result and complete
        job["result"] = result
        job["status"] = "COMPLETED"
        job["progress"] = 100
        _notify_callback(callback_url, job_id, "COMPLETED")
        log.info(f"[{job_id}] Tahlil tugadi!")

    except Exception as e:
        log.error(f"[{job_id}] Tahlil xatosi: {e}", exc_info=True)
        job["status"] = "FAILED"
        _notify_callback(callback_url, job_id, "FAILED", str(e))
    finally:
        # Cleanup
        if os.path.exists(local_video):
            os.remove(local_video)
        out_dir = f"/tmp/{job_id}_output"
        if os.path.exists(out_dir):
            import shutil
            shutil.rmtree(out_dir, ignore_errors=True)


def _run_real_analysis(job_id: str, video_path: str, calibration_points=None):
    """
    Run the actual ML pipeline from main.py components.
    Returns analytics JSON result.
    """
    import cv2
    import numpy as np
    import torch

    log.info(f"[{job_id}] ML pipeline boshlandi: {video_path}")
    log.info(f"[{job_id}] GPU: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'}")

    output_dir = f"/tmp/{job_id}_output"
    os.makedirs(output_dir, exist_ok=True)
    analytics_dir = os.path.join(output_dir, "analytics")
    os.makedirs(analytics_dir, exist_ok=True)

    # Import pipeline components
    from utils import read_video
    from trackers import Tracker
    from team_assigner import TeamAssigner
    from player_ball_assigner import PlayerBallAssigner
    from camera_movement_estimator import CameraMovementEstimator
    from view_transformer import ViewTransformer
    from speed_and_distance_estimator import SpeedAndDistance_Estimator
    from team_analytics import MatchAnalyzer

    # Progress updates
    def update_progress(pct, msg=""):
        jobs[job_id]["progress"] = pct
        log.info(f"[{job_id}] Progress {pct}%: {msg}")

    # Step 1: Read video
    update_progress(10, "Video o'qilmoqda...")
    video_frames = read_video(video_path)
    if len(video_frames) == 0:
        raise RuntimeError("Video o'qilmadi - codec yo'q yoki fayl buzilgan")
    h, w = video_frames[0].shape[:2]
    log.info(f"[{job_id}] {len(video_frames)} freym o'qildi ({w}x{h})")

    # Step 2: Tracking
    update_progress(20, "YOLO tracking...")
    tracker = Tracker(MODEL_PATH)
    tracks = tracker.get_object_tracks(video_frames, read_from_stub=False, stub_path=None)
    tracker.add_position_to_tracks(tracks)

    # Step 3: Camera movement
    update_progress(40, "Kamera harakati...")
    cam_estimator = CameraMovementEstimator(video_frames[0])
    cam_movement = cam_estimator.get_camera_movement(video_frames, read_from_stub=False, stub_path=None)
    cam_estimator.add_adjust_positions_to_tracks(tracks, cam_movement)

    # Step 4: View transform (with calibration from API if provided)
    update_progress(50, "Perspective transform...")
    calibration_json_path = os.path.join(output_dir, "calibration.json")
    court_length = 23.32  # default
    court_width = 68.0    # default

    if calibration_points and isinstance(calibration_points, dict):
        # Frontend sends calibration_points with pixel_vertices and optionally target_vertices
        try:
            cal_data = {}
            if 'pixel_vertices' in calibration_points:
                cal_data['pixel_vertices'] = calibration_points['pixel_vertices']
            if 'target_vertices' in calibration_points:
                cal_data['target_vertices'] = calibration_points['target_vertices']
            else:
                # Default: full field or half field based on what's visible
                cw = float(calibration_points.get('world_width', 68.0))
                cl = float(calibration_points.get('world_length', 105.0))
                cal_data['target_vertices'] = [
                    [0.0, cw], [0.0, 0.0], [cl, 0.0], [cl, cw]
                ]
            cal_data['world_width'] = float(calibration_points.get('world_width', 68.0))
            cal_data['world_length'] = float(calibration_points.get('world_length', 105.0))
            court_width = cal_data['world_width']
            court_length = cal_data['world_length']

            with open(calibration_json_path, 'w') as f:
                json.dump(cal_data, f, indent=2)
            log.info(f"[{job_id}] Calibration saved: {cal_data['world_length']}m x {cal_data['world_width']}m")
        except Exception as e:
            log.warning(f"[{job_id}] Calibration parse xato: {e}, default ishlatiladi")
            calibration_json_path = 'view_transformer/calibration.json'
    else:
        # No calibration — use default
        calibration_json_path = 'view_transformer/calibration.json'
        log.info(f"[{job_id}] No calibration points, using default")

    vt = ViewTransformer(calibration_path=calibration_json_path)
    # ViewTransformer'dan haqiqiy o'lchamlarni olish (default bo'lsa 23.32x68)
    # Bu MatchAnalyzer'ga to'g'ri o'lcham berishi muhim — aks holda tezliklar noto'g'ri chiqadi
    court_length = vt.world_length
    court_width = vt.world_width
    log.info(f"[{job_id}] ViewTransformer: {vt.info()} -> court {court_length}x{court_width}m")
    vt.add_transformed_position_to_tracks(tracks)

    # Step 5: Ball interpolation
    tracks["ball"] = tracker.interpolate_ball_positions(tracks["ball"])

    # Step 6: Speed & distance
    update_progress(60, "Tezlik va masofa...")
    sd_estimator = SpeedAndDistance_Estimator()
    sd_estimator.add_speed_and_distance_to_tracks(tracks)

    # Step 7: Team assignment
    update_progress(70, "Kamandalar ajratish...")
    team_assigner = TeamAssigner()
    team_assigner.assign_team_color(video_frames[0], tracks['players'][0])
    for frame_num, player_track in enumerate(tracks['players']):
        for pid, track in player_track.items():
            team = team_assigner.get_player_team(video_frames[frame_num], track['bbox'], pid)
            tracks['players'][frame_num][pid]['team'] = team
            tracks['players'][frame_num][pid]['team_color'] = team_assigner.team_colors[team]

    # Step 8: Ball ownership
    update_progress(80, "To'p egaligi...")
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

    # Step 9: Team Analytics
    update_progress(90, "Analitika...")
    analyzer = MatchAnalyzer(
        fps=24,
        court_width=court_width,
        court_length=court_length,
        output_dir=analytics_dir,
        preset='youtube',
    )
    analytics_results = analyzer.run(tracks, team_ball_control)
    analyzer.write_reports(analytics_results)

    # ── Step 10: Annotated video yaratish ──────────────────────────────
    update_progress(92, "Annotated video yaratilmoqda...")
    annotated_video_key = None
    try:
        import cv2
        # Draw annotations
        out_frames = tracker.draw_annotations(video_frames, tracks, team_ball_control)
        out_frames = cam_estimator.draw_camera_movement(out_frames, cam_movement)
        sd_estimator.draw_speed_and_distance(out_frames, tracks)

        # MP4 formatda saqlash (brauzer uchun)
        video_out_path = os.path.join(output_dir, f'{job_id}_annotated.mp4')
        _save_video_mp4(out_frames, video_out_path, fps=24)

        if os.path.exists(video_out_path):
            size_mb = os.path.getsize(video_out_path) / 1e6
            log.info(f"[{job_id}] Annotated video: {video_out_path} ({size_mb:.1f} MB)")

            # MinIO'ga yuklash
            annotated_video_key = f"analytics/{job_id}/annotated_video.mp4"
            _upload_to_s3(video_out_path, annotated_video_key, content_type='video/mp4')
            log.info(f"[{job_id}] Annotated video uploaded: {annotated_video_key}")

            # Disk'dan o'chirish (joy tejash)
            os.remove(video_out_path)

        # Xotirani bo'shatish
        del out_frames
    except Exception as e:
        log.warning(f"[{job_id}] Annotated video xato: {e}")
        import traceback
        traceback.print_exc()

    # Transform ML results to frontend-expected format
    raw = _make_serializable(analytics_results)
    result = _transform_to_frontend_format(raw, job_id, len(video_frames), w, h, fps=24,
                                            analytics_dir=analytics_dir,
                                            team_ball_control=team_ball_control,
                                            tracks=tracks)

    # Add metadata
    result["match_id"] = jobs[job_id]["match_id"]
    result["job_id"] = job_id
    result["video_frames_count"] = len(video_frames)
    result["video_resolution"] = f"{w}x{h}"
    if annotated_video_key:
        result["annotated_video_path"] = annotated_video_key

    # Also include raw ML pipeline output for detailed analysis
    result["raw_analytics"] = raw

    log.info(f"[{job_id}] Tahlil muvaffaqiyatli tugadi!")
    return result


def _transform_to_frontend_format(raw, job_id, total_frames, w, h, fps=24,
                                    analytics_dir=None, team_ball_control=None, tracks=None):
    """
    ML pipeline chiqarishini frontend kutgan formatga aylantiradi.

    ML output: {possession, speed, heatmap, passes, pressing, comparison, metadata}
    Frontend expects: {timeline, period_comparison, stamina_analysis, detected_players,
                       heatmap_paths, tactical_summary, match_phases, events}
    """
    import numpy as np
    meta = raw.get('metadata', {})
    possession = raw.get('possession', {})
    speed = raw.get('speed', {})
    heatmap_data = raw.get('heatmap', {})
    passes = raw.get('passes', {})
    pressing = raw.get('pressing', {})
    comparison = raw.get('comparison', {})
    teams = meta.get('teams', [1, 2])
    duration_s = meta.get('duration_s', 1)
    duration_min = duration_s / 60

    result = {}

    # ── detected_players: per-player GPS stats ──────────────────────
    detected_players = []
    for team in teams:
        team_key = f'team_{team}'
        team_speed = speed.get(team_key, {})
        per_player = team_speed.get('per_player', {})
        for pid_str, pstats in per_player.items():
            player = {
                'track_id': int(pid_str),
                'jersey_number': int(pid_str),  # ByteTrack ID as jersey (no real jersey detection)
                'team': team,
                'team_side': 'HOME' if team == 1 else 'AWAY',
                # GPS core stats
                'total_distance_m': pstats.get('total_distance_m', 0),
                'total_distance_km': pstats.get('total_distance_km', 0),
                'max_speed_kmh': pstats.get('max_speed_kmh', 0),
                'avg_speed_kmh': pstats.get('avg_speed_kmh', 0),
                'time_on_field_min': pstats.get('time_on_field_min', 0),
                'distance_per_min': pstats.get('distance_per_min', 0),
                'frames_tracked': pstats.get('frames_tracked', 0),
                # Speed zones
                'zone_distances_m': pstats.get('zone_distances_m', {}),
                'zone_time_pct': pstats.get('zone_time_pct', {}),
                # HSR & Sprint
                'high_speed_running_distance_m': pstats.get('high_speed_running_distance_m', 0),
                'sprint_distance_m': pstats.get('sprint_distance_m', 0),
                'sprints': pstats.get('sprint_count', 0),
                'sprint_details': pstats.get('sprint_details', []),
                # Accelerations
                'accelerations': pstats.get('accelerations', 0),
                'decelerations': pstats.get('decelerations', 0),
                'max_acceleration': pstats.get('max_acceleration', 0),
                'max_deceleration': pstats.get('max_deceleration', 0),
                # Appearances
                'appearances': pstats.get('frames_tracked', 0),
            }
            detected_players.append(player)
    result['detected_players'] = detected_players

    # ── Helper: compute stats for a time window from team_ball_control ──
    def _window_stats(start_frame, end_frame, tbc):
        """Compute stats for a frame range."""
        if tbc is None or len(tbc) == 0:
            return {}
        ef = min(end_frame, len(tbc))
        sf = max(0, start_frame)
        window_control = tbc[sf:ef]
        total = len(window_control)
        if total == 0:
            return {'home_possession_pct': 50, 'away_possession_pct': 50}
        home_pct = round(100.0 * np.sum(window_control == 1) / total, 1)
        away_pct = round(100.0 - home_pct, 1)
        return {'home_possession_pct': home_pct, 'away_possession_pct': away_pct}

    # ── timeline: 5-min windows ─────────────────────────────────────
    tbc = None
    if team_ball_control is not None:
        if hasattr(team_ball_control, 'tolist'):
            tbc = team_ball_control
        else:
            tbc = np.array(team_ball_control)

    window_min = 5
    timeline = []
    num_windows = max(1, int(np.ceil(duration_min / window_min)))
    for i in range(num_windows):
        w_start = i * window_min
        w_end = min((i + 1) * window_min, duration_min)
        sf = int(w_start * 60 * fps)
        ef = int(w_end * 60 * fps)

        ws = _window_stats(sf, ef, tbc)

        # Count sprints per team in this window
        home_sprints = 0
        away_sprints = 0
        for p in detected_players:
            for sd in p.get('sprint_details', []):
                sprint_frame = sd.get('start_frame', 0)
                if sf <= sprint_frame < ef:
                    if p['team'] == 1:
                        home_sprints += 1
                    else:
                        away_sprints += 1

        timeline.append({
            'window_start_min': round(w_start, 1),
            'window_end_min': round(w_end, 1),
            'phase': 'FIRST_HALF' if w_start < 45 else 'SECOND_HALF',
            'home_possession_pct': ws.get('home_possession_pct', 50),
            'away_possession_pct': ws.get('away_possession_pct', 50),
            'home_sprint_count': home_sprints,
            'away_sprint_count': away_sprints,
        })
    result['timeline'] = timeline

    # ── period_comparison: first half vs second half ────────────────
    half_frame = total_frames // 2
    first_stats = _window_stats(0, half_frame, tbc)
    second_stats = _window_stats(half_frame, total_frames, tbc)

    # Possession dominance info
    home_first = first_stats.get('home_possession_pct', 50)
    home_second = second_stats.get('home_possession_pct', 50)

    # Sprint counts per half
    home_sprints_h1, away_sprints_h1 = 0, 0
    home_sprints_h2, away_sprints_h2 = 0, 0
    for p in detected_players:
        for sd in p.get('sprint_details', []):
            sf_sprint = sd.get('start_frame', 0)
            if sf_sprint < half_frame:
                if p['team'] == 1: home_sprints_h1 += 1
                else: away_sprints_h1 += 1
            else:
                if p['team'] == 1: home_sprints_h2 += 1
                else: away_sprints_h2 += 1

    # Determine momentum shift
    diff1 = home_first - 50
    diff2 = home_second - 50
    if diff2 - diff1 > 5:
        momentum = 'HOME_GAINED'
    elif diff1 - diff2 > 5:
        momentum = 'AWAY_GAINED'
    else:
        momentum = 'STABLE'

    # Attacking third % (use heatmap zone data if available)
    home_atk_h1 = round(pressing.get('team_1', {}).get('high_press_pct', 30), 1)
    away_atk_h1 = round(pressing.get('team_2', {}).get('high_press_pct', 30), 1)

    period_comparison = {
        'first_half': {
            'home_possession_pct': home_first,
            'away_possession_pct': first_stats.get('away_possession_pct', 50),
            'home_sprint_count': home_sprints_h1,
            'away_sprint_count': away_sprints_h1,
            'home_attacking_third_pct': home_atk_h1,
            'away_attacking_third_pct': away_atk_h1,
            'home_defensive_third_pct': round(100 - home_atk_h1 - 40, 1),
            'away_defensive_third_pct': round(100 - away_atk_h1 - 40, 1),
        },
        'second_half': {
            'home_possession_pct': home_second,
            'away_possession_pct': second_stats.get('away_possession_pct', 50),
            'home_sprint_count': home_sprints_h2,
            'away_sprint_count': away_sprints_h2,
            'home_attacking_third_pct': home_atk_h1,
            'away_attacking_third_pct': away_atk_h1,
            'home_defensive_third_pct': round(100 - home_atk_h1 - 40, 1),
            'away_defensive_third_pct': round(100 - away_atk_h1 - 40, 1),
        },
        'momentum_shift': momentum,
        'insights': _generate_insights(raw, detected_players),
    }
    result['period_comparison'] = period_comparison

    # ── stamina_analysis: sprint buckets over 15-min intervals ──────
    num_buckets = 6  # 0-15, 15-30, 30-45, 45-60, 60-75, 75-90
    bucket_minutes = duration_min / num_buckets if duration_min > 0 else 5
    home_buckets = [0] * num_buckets
    away_buckets = [0] * num_buckets
    for p in detected_players:
        for sd in p.get('sprint_details', []):
            sprint_min = sd.get('start_frame', 0) / fps / 60
            bucket_idx = min(num_buckets - 1, int(sprint_min / bucket_minutes))
            if p['team'] == 1:
                home_buckets[bucket_idx] += 1
            else:
                away_buckets[bucket_idx] += 1

    # Fatigue detection: sprint count drops >50% in last third
    home_fatigue = False
    away_fatigue = False
    fatigue_min_home = None
    fatigue_min_away = None
    if num_buckets >= 3:
        home_early = sum(home_buckets[:num_buckets // 2]) or 1
        home_late = sum(home_buckets[num_buckets // 2:])
        if home_late < home_early * 0.5:
            home_fatigue = True
            fatigue_min_home = round(duration_min * 0.6, 0)

        away_early = sum(away_buckets[:num_buckets // 2]) or 1
        away_late = sum(away_buckets[num_buckets // 2:])
        if away_late < away_early * 0.5:
            away_fatigue = True
            fatigue_min_away = round(duration_min * 0.6, 0)

    result['stamina_analysis'] = {
        'sprint_buckets_home': home_buckets,
        'sprint_buckets_away': away_buckets,
        'home_fatigue_detected': home_fatigue,
        'away_fatigue_detected': away_fatigue,
        'fatigue_minute_home': fatigue_min_home,
        'fatigue_minute_away': fatigue_min_away,
    }

    # ── heatmap_paths: upload PNGs to MinIO ─────────────────────────
    heatmap_paths = {}
    if analytics_dir:
        for team in teams:
            png_name = f'heatmap_team_{team}.png'
            png_path = os.path.join(analytics_dir, png_name)
            if os.path.exists(png_path):
                minio_key = f"analytics/{job_id}/{png_name}"
                try:
                    s3.upload_file(png_path, S3_BUCKET, minio_key,
                                   ExtraArgs={'ContentType': 'image/png'})
                    label = 'team_a_heatmap' if team == teams[0] else 'team_b_heatmap'
                    heatmap_paths[label] = minio_key
                    log.info(f"[{job_id}] Heatmap uploaded: {minio_key}")
                except Exception as e:
                    log.warning(f"[{job_id}] Heatmap upload failed: {e}")
        # Pass network images too
        for team in teams:
            png_name = f'pass_network_team_{team}.png'
            png_path = os.path.join(analytics_dir, png_name)
            if os.path.exists(png_path):
                minio_key = f"analytics/{job_id}/{png_name}"
                try:
                    s3.upload_file(png_path, S3_BUCKET, minio_key,
                                   ExtraArgs={'ContentType': 'image/png'})
                    log.info(f"[{job_id}] Pass network uploaded: {minio_key}")
                except Exception as e:
                    log.warning(f"[{job_id}] Pass network upload failed: {e}")
    result['heatmap_paths'] = heatmap_paths

    # ── tactical_summary ────────────────────────────────────────────
    result['tactical_summary'] = {
        'team_a_formation': {'formation': 'N/A', 'positions': []},
        'team_b_formation': {'formation': 'N/A', 'positions': []},
        'home_team_name': 'Team A',
        'away_team_name': 'Team B',
    }
    result['home_team_name'] = 'Team A'
    result['away_team_name'] = 'Team B'

    # ── tactical_analysis: PPDA, transitions, recovery, danger zones, vulnerability, fatigue ──
    tactical = raw.get('tactical', {})
    if tactical:
        result['tactical_analysis'] = tactical

    # ── match_phases ────────────────────────────────────────────────
    if duration_min > 20:
        result['match_phases'] = [
            {'phase_type': 'FIRST_HALF', 'start_seconds': 0, 'end_seconds': duration_s / 2},
            {'phase_type': 'HALFTIME', 'start_seconds': duration_s / 2, 'end_seconds': duration_s / 2 + 1},
            {'phase_type': 'SECOND_HALF', 'start_seconds': duration_s / 2 + 1, 'end_seconds': duration_s},
        ]
    else:
        result['match_phases'] = [
            {'phase_type': 'FIRST_HALF', 'start_seconds': 0, 'end_seconds': duration_s},
        ]

    # ── events (empty for now — no goal detection yet) ──────────────
    result['events'] = []

    # ── Additional stats for backend extraction ─────────────────────
    result['total_frames_processed'] = total_frames
    result['confidence_average'] = 0.85  # Placeholder
    result['is_long_form'] = duration_min > 20

    return result


def _generate_insights(raw, detected_players):
    """Generate text insights in Uzbek from analysis."""
    insights = []
    possession = raw.get('possession', {})
    speed = raw.get('speed', {})
    comparison = raw.get('comparison', {})

    # Possession insight
    t1_poss = possession.get('team_1', {}).get('possession_pct', 0)
    t2_poss = possession.get('team_2', {}).get('possession_pct', 0)
    if t1_poss > t2_poss + 10:
        insights.append(f"Team A to'pni ko'proq nazorat qildi ({t1_poss}% vs {t2_poss}%)")
    elif t2_poss > t1_poss + 10:
        insights.append(f"Team B to'pni ko'proq nazorat qildi ({t2_poss}% vs {t1_poss}%)")
    else:
        insights.append(f"To'p nazorati teng taqsimlandi ({t1_poss}% - {t2_poss}%)")

    # Speed insight
    t1_speed = speed.get('team_1', {})
    t2_speed = speed.get('team_2', {})
    t1_dist = t1_speed.get('total_distance_m', 0)
    t2_dist = t2_speed.get('total_distance_m', 0)
    if t1_dist > 0 and t2_dist > 0:
        if t1_dist > t2_dist + 300:
            insights.append(f"Team A ko'proq masofa bosdi ({t1_dist:.0f}m vs {t2_dist:.0f}m)")
        elif t2_dist > t1_dist + 300:
            insights.append(f"Team B ko'proq masofa bosdi ({t2_dist:.0f}m vs {t1_dist:.0f}m)")

    # Sprint insight
    t1_sprints = t1_speed.get('total_sprints', 0)
    t2_sprints = t2_speed.get('total_sprints', 0)
    if t1_sprints > 0 or t2_sprints > 0:
        insights.append(f"Sprint soni: Team A — {t1_sprints}, Team B — {t2_sprints}")

    # Top speed
    t1_max = t1_speed.get('max_speed_kmh', 0)
    t2_max = t2_speed.get('max_speed_kmh', 0)
    max_speed = max(t1_max, t2_max)
    if max_speed > 0:
        fast_team = 'A' if t1_max > t2_max else 'B'
        insights.append(f"Eng yuqori tezlik: {max_speed} km/h (Team {fast_team})")

    # Per-player highlight
    if detected_players:
        top_runner = max(detected_players, key=lambda p: p.get('total_distance_m', 0))
        if top_runner.get('total_distance_m', 0) > 0:
            side = 'A' if top_runner['team'] == 1 else 'B'
            insights.append(
                f"Eng ko'p yugurgan: #{top_runner['track_id']} (Team {side}) — "
                f"{top_runner['total_distance_m']:.0f}m, max {top_runner['max_speed_kmh']} km/h"
            )

    # Narrative from comparison
    narrative = comparison.get('narrative', '')
    if narrative:
        insights.append(narrative)

    return insights


def _save_video_mp4(frames, output_path, fps=24):
    """
    Video frame'larni MP4 (H.264) formatda saqlash.
    Brauzer moslik uchun mp4v yoki avc1 codec ishlatiladi.
    """
    import cv2
    h, w = frames[0].shape[:2]

    # H.264 codec — brauzer uchun eng yaxshi
    for codec in ['avc1', 'h264', 'mp4v', 'XVID']:
        fourcc = cv2.VideoWriter_fourcc(*codec)
        out = cv2.VideoWriter(output_path, fourcc, fps, (w, h))
        if out.isOpened():
            break
        out.release()
    else:
        # Fallback — AVI
        fourcc = cv2.VideoWriter_fourcc(*'XVID')
        output_path = output_path.replace('.mp4', '.avi')
        out = cv2.VideoWriter(output_path, fourcc, fps, (w, h))

    for frame in frames:
        out.write(frame)
    out.release()

    # Agar ffmpeg mavjud bo'lsa, qayta encode qilish (brauzer uchun to'g'ri format)
    import subprocess
    import shutil
    if shutil.which('ffmpeg') and output_path.endswith('.mp4'):
        tmp_path = output_path + '.tmp.mp4'
        try:
            subprocess.run([
                'ffmpeg', '-y', '-i', output_path,
                '-c:v', 'libx264', '-preset', 'fast', '-crf', '23',
                '-pix_fmt', 'yuv420p',  # brauzer moslik
                '-movflags', '+faststart',  # streaming uchun
                tmp_path
            ], capture_output=True, timeout=300)
            if os.path.exists(tmp_path) and os.path.getsize(tmp_path) > 0:
                os.replace(tmp_path, output_path)
        except Exception as e:
            log.warning(f"ffmpeg re-encode failed: {e}")
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    return output_path


def _upload_to_s3(file_path, key, content_type='application/octet-stream'):
    """Faylni MinIO/S3'ga yuklash."""
    s3 = boto3.client('s3',
                      endpoint_url=S3_ENDPOINT,
                      aws_access_key_id=S3_ACCESS_KEY,
                      aws_secret_access_key=S3_SECRET_KEY,
                      region_name='us-east-1')
    s3.upload_file(file_path, S3_BUCKET, key,
                   ExtraArgs={'ContentType': content_type})


def _make_serializable(obj):
    """Convert numpy types to Python native for JSON serialization."""
    import numpy as np
    if isinstance(obj, dict):
        return {k: _make_serializable(v) for k, v in obj.items()}
    elif isinstance(obj, (list, tuple)):
        return [_make_serializable(i) for i in obj]
    elif isinstance(obj, np.integer):
        return int(obj)
    elif isinstance(obj, np.floating):
        return float(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    elif isinstance(obj, np.bool_):
        return bool(obj)
    return obj


# ── Callback ─────────────────────────────────────────────────────────
def _notify_callback(callback_url: Optional[str], job_id: str, status: str, error: Optional[str] = None):
    """Spring Boot'ga tahlil holatini bildirish."""
    if not callback_url:
        return
    try:
        body = {"job_id": job_id, "status": status}
        if error:
            body["error"] = error
        if job_id in jobs:
            body["progress"] = jobs[job_id].get("progress", 0)
        headers = {"Authorization": f"Bearer {INTERNAL_TOKEN}"}
        requests.post(callback_url, json=body, headers=headers, timeout=10)
        log.info(f"[{job_id}] Callback yuborildi: {callback_url} -> {status}")
    except Exception as e:
        log.warning(f"[{job_id}] Callback xato: {e}")


# ── Main ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", "8000"))
    log.info(f"ML API server ishga tushmoqda: port={port}")
    uvicorn.run(app, host="0.0.0.0", port=port)

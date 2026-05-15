"""
Speed and Distance Estimator (Enhanced)
========================================
Original loyiha bilan mos interfeys, lekin sezilarli yaxshilanishlar:

1. ZeroDivisionError tuzatilgan (oxirgi freym buguri)
2. Pozitsiya smoothing - har oyinchi uchun rolling mean (default 5 freym)
   bbox jitter tezlik spike'larini keltirib chiqaradi, smoothing buni yo'qotadi
3. Tezlik outlier rejection - real futbolchi >36 km/h yugura olmaydi (Mbappé rekordi),
   shu sababli >40 km/h qiymatlar noise deb hisoblanib clip qilinadi
4. Distance noaniqlik filtri - juda kichik smoothing artefaktlari yig'ilmaydi
"""
import cv2
import sys
sys.path.append('../')
from utils import measure_distance, get_foot_position

# Ufqiy chegaralar
MAX_REALISTIC_SPEED_KMH = 40.0  # Mbappé rekordi 36 km/h, 40 ham juda yuqori
MIN_DISTANCE_INCREMENT = 0.1    # 10cm dan kichik harakat = noise


class SpeedAndDistance_Estimator:
    def __init__(self, frame_window=5, frame_rate=24,
                 smoothing_window=5, max_speed_kmh=MAX_REALISTIC_SPEED_KMH):
        self.frame_window = frame_window
        self.frame_rate = frame_rate
        self.smoothing_window = smoothing_window
        self.max_speed_kmh = max_speed_kmh

    def _smooth_positions(self, tracks):
        """
        Har oyinchi uchun position_transformed'ni rolling mean orqali smooth qilish.
        Bu bbox jitter va tracking noise'ini kamaytiradi.
        """
        if self.smoothing_window <= 1:
            return  # smoothing o'chirilgan

        for object_name, object_tracks in tracks.items():
            if object_name in ('ball', 'referees'):
                continue

            # Har track_id uchun pozitsiyalarni yig'ib, smoothing qo'llash
            # Avval (track_id -> [(frame_num, [x,y]),...]) ni tuzamiz
            per_id = {}
            for frame_num, frame_tracks in enumerate(object_tracks):
                for tid, info in frame_tracks.items():
                    pos = info.get('position_transformed')
                    if pos is None:
                        continue
                    per_id.setdefault(tid, []).append((frame_num, pos))

            # Smooth qilingan qiymatlarni yozamiz
            for tid, frames in per_id.items():
                if len(frames) < 2:
                    continue
                # Rolling mean - faqat ketma-ket freymlar uchun
                w = self.smoothing_window
                for i in range(len(frames)):
                    window_start = max(0, i - w // 2)
                    window_end = min(len(frames), i + w // 2 + 1)
                    window = frames[window_start:window_end]
                    avg_x = sum(p[1][0] for p in window) / len(window)
                    avg_y = sum(p[1][1] for p in window) / len(window)
                    frame_num = frames[i][0]
                    tracks[object_name][frame_num][tid]['position_smoothed'] = [avg_x, avg_y]

    def add_speed_and_distance_to_tracks(self, tracks):
        # Avval pozitsiyalarni smooth qilamiz
        self._smooth_positions(tracks)

        total_distance = {}

        for object_name, object_tracks in tracks.items():
            if object_name in ('ball', 'referees'):
                continue

            number_of_frames = len(object_tracks)
            for frame_num in range(0, number_of_frames, self.frame_window):
                last_frame = min(frame_num + self.frame_window, number_of_frames - 1)

                for track_id, _ in object_tracks[frame_num].items():
                    if track_id not in object_tracks[last_frame]:
                        continue

                    # Smooth qilingan pozitsiyalardan foydalanish (mavjud bo'lsa)
                    start_info = object_tracks[frame_num][track_id]
                    end_info = object_tracks[last_frame][track_id]
                    start_position = start_info.get('position_smoothed') or start_info.get('position_transformed')
                    end_position = end_info.get('position_smoothed') or end_info.get('position_transformed')

                    if start_position is None or end_position is None:
                        continue

                    distance_covered = measure_distance(start_position, end_position)
                    time_elapsed = (last_frame - frame_num) / self.frame_rate

                    # Bug fix: nolga bo'lish (oxirgi freym buguri)
                    if time_elapsed <= 0:
                        continue

                    # Smoothing artefakti - juda kichik harakatlarni e'tiborga olmaymiz
                    if distance_covered < MIN_DISTANCE_INCREMENT:
                        speed_km_per_hour = 0.0
                    else:
                        speed_meters_per_second = distance_covered / time_elapsed
                        speed_km_per_hour = speed_meters_per_second * 3.6

                    # Outlier rejection: real futbolchi >40 km/h yugura olmaydi
                    if speed_km_per_hour > self.max_speed_kmh:
                        # Pozitsiya sakraganini ko'rsatadi (ID switch yoki tracking xato)
                        # Bu freym uchun tezlik 0 deb belgilaymiz, distance qo'shmaymiz
                        speed_km_per_hour = 0.0
                        distance_covered = 0.0

                    if object_name not in total_distance:
                        total_distance[object_name] = {}
                    if track_id not in total_distance[object_name]:
                        total_distance[object_name][track_id] = 0
                    total_distance[object_name][track_id] += distance_covered

                    for frame_num_batch in range(frame_num, last_frame):
                        if track_id not in tracks[object_name][frame_num_batch]:
                            continue
                        tracks[object_name][frame_num_batch][track_id]['speed'] = speed_km_per_hour
                        tracks[object_name][frame_num_batch][track_id]['distance'] = total_distance[object_name][track_id]

    def draw_speed_and_distance(self, frames, tracks):
        output_frames = []
        for frame_num, frame in enumerate(frames):
            for object_name, object_tracks in tracks.items():
                if object_name in ('ball', 'referees'):
                    continue
                if frame_num >= len(object_tracks):
                    continue
                for _, track_info in object_tracks[frame_num].items():
                    if 'speed' not in track_info:
                        continue
                    speed = track_info.get('speed')
                    distance = track_info.get('distance')
                    if speed is None or distance is None:
                        continue

                    bbox = track_info['bbox']
                    position = get_foot_position(bbox)
                    position = list(position)
                    position[1] += 40
                    position = tuple(map(int, position))
                    cv2.putText(frame, f"{speed:.1f} km/h", position,
                                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 2)
                    cv2.putText(frame, f"{distance:.1f} m",
                                (position[0], position[1] + 20),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 2)
            output_frames.append(frame)
        return output_frames

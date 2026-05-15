"""
View Transformer
================
Piksel koordinatalarni real maydon koordinatalariga (metr) aylantiradi.

Kalibrlash JSON fayldan yuklanadi: view_transformer/calibration.json
Agar JSON yo'q bo'lsa, original loyiha defaultiga qaytadi (08fd33_4.mp4 uchun).

Yangi kalibrlash yaratish uchun:
    python tools/calibrate_view_interactive.py --video input_videos/your.mp4
"""
import json
import os
import warnings
import numpy as np
import cv2


# Original loyiha (08fd33_4.mp4) uchun fallback kalibrlash
DEFAULT_PIXEL_VERTICES = [
    [110, 1035],
    [265, 275],
    [910, 260],
    [1640, 915],
]
DEFAULT_WORLD_WIDTH = 68.0
DEFAULT_WORLD_LENGTH = 23.32


class ViewTransformer:
    """
    Perspektiv transformatsiya orqali piksel -> metr.

    Konventsiya (kalibrlashda klik tartibi):
        pixel_vertices[0] -> target (0, world_width)         # pastki-chap
        pixel_vertices[1] -> target (0, 0)                   # yuqori-chap
        pixel_vertices[2] -> target (world_length, 0)        # yuqori-o'ng
        pixel_vertices[3] -> target (world_length, width)    # pastki-o'ng

    Default rejimda (calibration.json yo'q): 23.32m × 68m kichik to'rtburchak
    08fd33_4.mp4 dagi maydonning bir qismi uchun. Boshqa videoda bu noto'g'ri
    bo'ladi - calibration.json yarating.
    """

    def __init__(self, calibration_path='view_transformer/calibration.json'):
        self.calibration_path = calibration_path
        self.calibration_loaded_from = None
        self._load_calibration()

    def _load_calibration(self):
        """JSON dan o'qish, yo'q bo'lsa default'larga qaytish"""
        if os.path.exists(self.calibration_path):
            try:
                with open(self.calibration_path, 'r', encoding='utf-8') as f:
                    cal = json.load(f)
                pixel_vertices = cal['pixel_vertices']
                target_vertices = cal['target_vertices']
                self.world_width = float(cal.get('world_width', DEFAULT_WORLD_WIDTH))
                self.world_length = float(cal.get('world_length', DEFAULT_WORLD_LENGTH))
                self.calibration_loaded_from = self.calibration_path
            except (KeyError, ValueError, json.JSONDecodeError) as e:
                warnings.warn(
                    f"calibration.json buzuq ({e}), default'ga qaytarildi. "
                    f"Yangi kalibrlash uchun: python tools/calibrate_view_interactive.py"
                )
                pixel_vertices = DEFAULT_PIXEL_VERTICES
                target_vertices = self._default_target()
                self.world_width = DEFAULT_WORLD_WIDTH
                self.world_length = DEFAULT_WORLD_LENGTH
                self.calibration_loaded_from = 'default (fallback)'
        else:
            pixel_vertices = DEFAULT_PIXEL_VERTICES
            target_vertices = self._default_target()
            self.world_width = DEFAULT_WORLD_WIDTH
            self.world_length = DEFAULT_WORLD_LENGTH
            self.calibration_loaded_from = 'default (08fd33_4.mp4)'

        self.pixel_vertices = np.array(pixel_vertices, dtype=np.float32)
        self.target_vertices = np.array(target_vertices, dtype=np.float32)
        self.perspective_transformer = cv2.getPerspectiveTransform(
            self.pixel_vertices, self.target_vertices
        )

    @staticmethod
    def _default_target():
        return [
            [0.0, DEFAULT_WORLD_WIDTH],
            [0.0, 0.0],
            [DEFAULT_WORLD_LENGTH, 0.0],
            [DEFAULT_WORLD_LENGTH, DEFAULT_WORLD_WIDTH],
        ]

    def info(self):
        """Foydalanuvchi uchun qisqa ma'lumot"""
        return (
            f"ViewTransformer: {self.calibration_loaded_from} | "
            f"{self.world_length:.1f}m × {self.world_width:.1f}m"
        )

    def transform_point(self, point, allow_outside=True):
        """
        Piksel nuqtani metr koordinataga aylantirish.

        Args:
            point: np.ndarray [x, y] piksel
            allow_outside: True bo'lsa, kalibrlangan to'rtburchak tashqarisidagi
                nuqtalar ham transform qilinadi (extrapolation). False bo'lsa,
                tashqarida bo'lganlar None qaytariladi (eski xulq).

        Returns:
            np.ndarray shape (1, 2) yoki None
        """
        try:
            p = (int(point[0]), int(point[1]))
        except (TypeError, ValueError):
            return None

        if not allow_outside:
            is_inside = cv2.pointPolygonTest(self.pixel_vertices, p, False) >= 0
            if not is_inside:
                return None

        reshaped = np.array(point, dtype=np.float32).reshape(-1, 1, 2)
        transformed = cv2.perspectiveTransform(reshaped, self.perspective_transformer)
        return transformed.reshape(-1, 2)

    def add_transformed_position_to_tracks(self, tracks, allow_outside=True):
        """
        Har bir oyinchining position_adjusted ni position_transformed ga aylantirish.

        allow_outside=True: kalibrlangan zona tashqarisidagi oyinchilar ham
            transform qilinadi (kichik kalibrlash bilan ham boshqa oyinchilar
            yo'qolmaydi). Lekin extrapolation - aniqligi past bo'ladi.
        """
        for object_name, object_tracks in tracks.items():
            for frame_num, track in enumerate(object_tracks):
                for track_id, track_info in track.items():
                    position = track_info.get('position_adjusted')
                    if position is None:
                        tracks[object_name][frame_num][track_id]['position_transformed'] = None
                        continue
                    position = np.array(position)
                    transformed = self.transform_point(position, allow_outside=allow_outside)
                    if transformed is not None:
                        result = transformed.squeeze().tolist()
                        # Real maydon chegarasiga clip qilish (extrapolation noto'g'ri
                        # qiymatlar berishi mumkin)
                        if isinstance(result, list) and len(result) == 2:
                            x, y = result
                            # Bardoshli chegaralar bilan clip
                            margin = 5.0  # 5m tashqariga ruxsat
                            x = max(-margin, min(self.world_length + margin, x))
                            y = max(-margin, min(self.world_width + margin, y))
                            result = [x, y]
                        tracks[object_name][frame_num][track_id]['position_transformed'] = result
                    else:
                        tracks[object_name][frame_num][track_id]['position_transformed'] = None

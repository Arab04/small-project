"""
Team Assigner
=============
KMeans bo'yicha oyinchi formasidagi rangni 2 ta klasterga ajratadi.

Yaxshilanishlar:
- Original kodda hardcoded "if player_id == 91: team_id = 1" qoldiqi - olib tashlandi
- KMeans n_init=10 (default 1) - barqarorroq natija
- Crop validation - bbox tashqarida bo'lsa skip
- Empty crop handling
"""
import numpy as np
from sklearn.cluster import KMeans


class TeamAssigner:
    def __init__(self):
        self.team_colors = {}
        self.player_team_dict = {}
        self.kmeans = None

    def get_clustering_model(self, image):
        """Pixellarni 2 klasterga ajratish (oyinchi rangi vs fon)"""
        image_2d = image.reshape(-1, 3)
        kmeans = KMeans(n_clusters=2, init="k-means++", n_init=1, random_state=42)
        kmeans.fit(image_2d)
        return kmeans

    def get_player_color(self, frame, bbox):
        """Oyinchi formasi rangini chiqarish (bbox ning yuqori yarmidan)"""
        h, w = frame.shape[:2]
        x1, y1, x2, y2 = [int(v) for v in bbox]
        # Frame chegaralariga clip
        x1, y1 = max(0, x1), max(0, y1)
        x2, y2 = min(w, x2), min(h, y2)

        if x2 <= x1 or y2 <= y1:
            return np.array([128, 128, 128])  # default kulrang

        image = frame[y1:y2, x1:x2]
        if image.size == 0:
            return np.array([128, 128, 128])

        # Faqat yuqori yarim (forma rangi - shimda emas)
        top_half_image = image[:image.shape[0] // 2, :]
        if top_half_image.size == 0:
            return np.array([128, 128, 128])

        kmeans = self.get_clustering_model(top_half_image)
        labels = kmeans.labels_
        clustered_image = labels.reshape(top_half_image.shape[0], top_half_image.shape[1])

        # Burchaklarda ko'p uchragan klaster - "fon" (maydon)
        corner_clusters = [
            clustered_image[0, 0], clustered_image[0, -1],
            clustered_image[-1, 0], clustered_image[-1, -1]
        ]
        non_player_cluster = max(set(corner_clusters), key=corner_clusters.count)
        player_cluster = 1 - non_player_cluster
        return kmeans.cluster_centers_[player_cluster]

    def assign_team_color(self, frame, player_detections):
        """Birinchi freym asosida ikki kamandaning rang markazlarini topish"""
        player_colors = []
        for _, player_detection in player_detections.items():
            bbox = player_detection["bbox"]
            color = self.get_player_color(frame, bbox)
            player_colors.append(color)

        if len(player_colors) < 2:
            # Default: ko'pgina futbol kiyimlari uchun
            self.team_colors[1] = np.array([255, 255, 255])  # oq
            self.team_colors[2] = np.array([50, 50, 200])     # ko'k
            return

        # n_init=10 - barqarorroq KMeans (default 1 ga qarama-qarshi)
        kmeans = KMeans(n_clusters=2, init="k-means++", n_init=10, random_state=42)
        kmeans.fit(player_colors)
        self.kmeans = kmeans
        self.team_colors[1] = kmeans.cluster_centers_[0]
        self.team_colors[2] = kmeans.cluster_centers_[1]

    def get_player_team(self, frame, player_bbox, player_id):
        """Cached team yoki yangi predict"""
        if player_id in self.player_team_dict:
            return self.player_team_dict[player_id]

        if self.kmeans is None:
            return 1  # fallback

        player_color = self.get_player_color(frame, player_bbox)
        team_id = self.kmeans.predict(player_color.reshape(1, -1))[0]
        team_id += 1   # 0/1 -> 1/2

        # NOTE: original loyihada "if player_id == 91: team_id = 1" qoldiqi
        # bor edi (08fd33_4.mp4 dagi muayyan oyinchi uchun manual override).
        # Boshqa videolarda bu noto'g'ri ishlaydi - olib tashlandi.

        self.player_team_dict[player_id] = team_id
        return team_id

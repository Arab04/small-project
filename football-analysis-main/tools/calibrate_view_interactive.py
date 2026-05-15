"""
Interactive View Transformer Calibration
=========================================
PyCharm/Jupyter da matplotlib oynasi orqali maydonning 4 burchagini
sichqoncha bilan klik qilib kalibrlash. Natija JSON faylga saqlanadi
va ViewTransformer avtomatik o'qiydi.

Foydalanish (PyCharm Python Console yoki run script):
    python tools/calibrate_view_interactive.py \
        --video input_videos/match.mp4 \
        --frame 0 \
        --preset half_pitch

Yoki interaktiv:
    python tools/calibrate_view_interactive.py --video input_videos/match.mp4

KLIK TARTIBI (juda muhim!):
    Maydonning 4 burchagini AYLANIB klik qiling, masalan:
      1. Pastki-chap (yon chiziq + orqa chiziq kesishuvi)
      2. Yuqori-chap (yon chiziq + markaz chiziq kesishuvi)
      3. Yuqori-o'ng (qarama-qarshi yon chiziq + markaz chiziq)
      4. Pastki-o'ng (qarama-qarshi yon chiziq + orqa chiziq)

Tartibi muhim - bu to'rtburchakning haqiqiy o'lchamlarini aniqlaydi.

PRESET'LAR (o'lchamlari):
  - full_pitch:  105m × 68m (butun maydon, 4 burchagi)
  - half_pitch:  52.5m × 68m (yarmi - tavsiya etiladi)
  - penalty_box: 16.5m × 40.32m (jarima maydonchasi)
  - center_box:  20m × 30m (markaz atrofidagi shartli to'rtburchak)
  - custom:      o'zingiz kiritasiz
"""
import argparse
import json
import os
import sys
import cv2
import numpy as np

try:
    import matplotlib
    import matplotlib.pyplot as plt
    from matplotlib.patches import Polygon
    HAS_MPL = True
except ImportError:
    HAS_MPL = False


PRESETS = {
    'full_pitch': {
        'width': 105.0,
        'length': 68.0,
        'description': 'Butun maydon (105m × 68m). 4 burchak: korner flagchalari',
    },
    'half_pitch': {
        'width': 52.5,
        'length': 68.0,
        'description': 'Yarim maydon (52.5m × 68m). 4 burchak: 2 korner + 2 markaz chiziq',
    },
    'penalty_box': {
        'width': 16.5,
        'length': 40.32,
        'description': 'Jarima maydonchasi (16.5m × 40.32m)',
    },
    'center_box': {
        'width': 20.0,
        'length': 30.0,
        'description': 'Markaz atrofidagi 20×30m shartli to\'rtburchak',
    },
}


class InteractiveCalibrator:
    """
    Matplotlib oynasini ochib, foydalanuvchidan 4 nuqta klik talab qiladi.
    Klik tartibi: pastki-chap → yuqori-chap → yuqori-o'ng → pastki-o'ng
    """

    def __init__(self, frame, world_width, world_length):
        self.frame = frame
        self.world_width = world_width
        self.world_length = world_length
        self.points = []
        self.fig = None
        self.ax = None
        self.scatter = None
        self.polygon = None
        self.labels = ['1: Pastki-Chap', '2: Yuqori-Chap',
                       '3: Yuqori-O\'ng', '4: Pastki-O\'ng']

    def run(self):
        if not HAS_MPL:
            raise RuntimeError("matplotlib o'rnatilmagan: pip install matplotlib")

        self.fig, self.ax = plt.subplots(figsize=(14, 8))
        # OpenCV BGR -> matplotlib RGB
        rgb = cv2.cvtColor(self.frame, cv2.COLOR_BGR2RGB)
        self.ax.imshow(rgb)
        self.ax.set_title(self._title())
        self.ax.set_xlabel('X piksel')
        self.ax.set_ylabel('Y piksel')

        self.fig.canvas.mpl_connect('button_press_event', self._on_click)
        self.fig.canvas.mpl_connect('key_press_event', self._on_key)

        plt.tight_layout()
        plt.show()

        if len(self.points) != 4:
            print(f"❌ Faqat {len(self.points)} nuqta klik qilindi, 4 ta kerak edi")
            return None
        return self.points

    def _title(self):
        n = len(self.points)
        if n < 4:
            return (f'KLIK QILING [{n+1}/4]: {self.labels[n]}\n'
                    f'(Z = oxirgi nuqtani bekor qilish, R = qaytadan, ENTER = tayyor)')
        return f'✅ 4 nuqta tayyor. ENTER = saqlash, R = qaytadan'

    def _redraw(self):
        # Eski markerlarni o'chirish
        for collection in list(self.ax.collections):
            collection.remove()
        for patch in list(self.ax.patches):
            patch.remove()
        for txt in list(self.ax.texts):
            txt.remove()

        # Nuqtalarni chizish
        if self.points:
            xs = [p[0] for p in self.points]
            ys = [p[1] for p in self.points]
            colors = ['#ff4444', '#44ff44', '#4444ff', '#ffaa00']
            for i, (x, y) in enumerate(self.points):
                self.ax.scatter([x], [y], c=colors[i], s=200,
                                edgecolors='white', linewidths=2, zorder=5)
                self.ax.annotate(f'{i+1}', (x, y), color='white',
                                 fontsize=14, fontweight='bold',
                                 ha='center', va='center', zorder=6)

        # 4 ta nuqta to'lganda to'rtburchak chizish
        if len(self.points) == 4:
            poly = Polygon(self.points, fill=True, facecolor='yellow',
                           edgecolor='yellow', alpha=0.2, linewidth=2)
            self.ax.add_patch(poly)

        self.ax.set_title(self._title())
        self.fig.canvas.draw_idle()

    def _on_click(self, event):
        if event.inaxes != self.ax:
            return
        if len(self.points) >= 4:
            return
        if event.xdata is None or event.ydata is None:
            return
        self.points.append((float(event.xdata), float(event.ydata)))
        print(f'  Nuqta {len(self.points)}: ({event.xdata:.0f}, {event.ydata:.0f})')
        self._redraw()

    def _on_key(self, event):
        if event.key == 'z' and self.points:
            removed = self.points.pop()
            print(f'  Bekor qilindi: ({removed[0]:.0f}, {removed[1]:.0f})')
            self._redraw()
        elif event.key == 'r':
            print('  Qaytadan boshlash')
            self.points = []
            self._redraw()
        elif event.key == 'enter' or event.key == 'return':
            if len(self.points) == 4:
                plt.close(self.fig)
            else:
                print(f'  Hali {4 - len(self.points)} ta nuqta kerak')


def extract_frame(video_path, frame_num=0):
    """Videoning ma'lum freymini o'qib, np.ndarray qaytaradi"""
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"Video ochilmadi: {video_path}")
    cap.set(cv2.CAP_PROP_POS_FRAMES, frame_num)
    ret, frame = cap.read()
    cap.release()
    if not ret:
        raise RuntimeError(f"Freym {frame_num} o'qilmadi")
    return frame


def save_calibration(points, world_width, world_length,
                     video_path, frame_num, output_path):
    """Kalibrlash natijasini JSON'ga saqlash"""
    # ViewTransformer'ning konventsiyasi:
    #   target[0] = (0, court_width)        ← pastki-chap (world_length=0, world_width=W)
    #   target[1] = (0, 0)                  ← yuqori-chap
    #   target[2] = (court_length, 0)       ← yuqori-o'ng
    #   target[3] = (court_length, court_width)  ← pastki-o'ng
    target_vertices = [
        [0.0, world_width],
        [0.0, 0.0],
        [world_length, 0.0],
        [world_length, world_width],
    ]
    data = {
        'pixel_vertices': [[float(p[0]), float(p[1])] for p in points],
        'target_vertices': target_vertices,
        'world_width': world_width,
        'world_length': world_length,
        'video_source': os.path.basename(video_path) if video_path else None,
        'frame_num': frame_num,
        'note': (
            "pixel_vertices: rasmda klik qilingan nuqtalar (pastki-chap, "
            "yuqori-chap, yuqori-o'ng, pastki-o'ng). target_vertices: shu "
            "to'rtburchakning haqiqiy o'lchamlari (metr). ViewTransformer "
            "shu fayl mavjud bo'lsa avtomatik yuklaydi."
        ),
    }
    os.makedirs(os.path.dirname(output_path) or '.', exist_ok=True)
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    return data


def parse_args():
    p = argparse.ArgumentParser(description='Interactive View Transformer Calibration')
    p.add_argument('--video', required=False, help='Video fayli')
    p.add_argument('--frame', type=int, default=0, help='Klik uchun freym raqami (default 0)')
    p.add_argument('--preset', choices=list(PRESETS.keys()) + ['custom'],
                   default='half_pitch',
                   help='Maydon o\'lchami presetlari (default: half_pitch)')
    p.add_argument('--width', type=float,
                   help='Custom: maydon kengligi (m), preset=custom uchun')
    p.add_argument('--length', type=float,
                   help='Custom: maydon uzunligi (m), preset=custom uchun')
    p.add_argument('--output', default='view_transformer/calibration.json',
                   help='Saqlash yo\'li')
    p.add_argument('--manual', nargs=4, type=str,
                   help='Manual rejim: 4 nuqta "X,Y" formatida (klik qilinmaydi). '
                        'Masalan: --manual 110,1035 265,275 910,260 1640,915')
    return p.parse_args()


def main():
    args = parse_args()

    # Maydon o'lchamlarini aniqlash
    if args.preset == 'custom':
        if args.width is None or args.length is None:
            print("❌ --preset custom uchun --width va --length kerak", file=sys.stderr)
            sys.exit(1)
        world_width = args.width
        world_length = args.length
    else:
        preset = PRESETS[args.preset]
        world_width = preset['width']
        world_length = preset['length']
        print(f"📐 Preset: {args.preset} - {preset['description']}")

    print(f"   Maydon o'lchami: {world_length}m (X) × {world_width}m (Y)")
    print()

    # Manual rejim - klik qilmasdan koordinatalarni argumentdan olish
    if args.manual:
        try:
            points = [tuple(float(v) for v in s.split(',')) for s in args.manual]
        except (ValueError, IndexError):
            print("❌ --manual format: 'X,Y' (4 ta), masalan: 110,1035", file=sys.stderr)
            sys.exit(1)
        print(f"📌 Manual nuqtalar: {points}")
    else:
        if not args.video:
            print("❌ --video kerak (yoki --manual bilan)", file=sys.stderr)
            sys.exit(1)
        if not os.path.exists(args.video):
            print(f"❌ Video topilmadi: {args.video}", file=sys.stderr)
            sys.exit(1)

        print(f"🎬 Video: {args.video}, freym: {args.frame}")
        print("   Freym o'qilmoqda...")
        frame = extract_frame(args.video, args.frame)
        h, w = frame.shape[:2]
        print(f"   Rezolyutsiya: {w}×{h}")
        print()
        print("📋 KO'RSATMA:")
        print("   Matplotlib oynasi ochiladi. To'rtburchakning 4 burchagini")
        print("   AYLANIB klik qiling shu tartibda:")
        print("     1) Pastki-chap")
        print("     2) Yuqori-chap")
        print("     3) Yuqori-o'ng")
        print("     4) Pastki-o'ng")
        print()
        print("   Klaviatura:")
        print("     Z     - oxirgi nuqtani bekor qilish")
        print("     R     - qaytadan boshlash")
        print("     ENTER - 4 nuqta to'lgach saqlash")
        print()

        cal = InteractiveCalibrator(frame, world_width, world_length)
        points = cal.run()
        if points is None:
            sys.exit(1)

    # Saqlash
    data = save_calibration(
        points=points,
        world_width=world_width,
        world_length=world_length,
        video_path=args.video,
        frame_num=args.frame,
        output_path=args.output,
    )

    print()
    print("✅ Saqlandi:", args.output)
    print()
    print("Natija:")
    print(json.dumps(data, indent=2, ensure_ascii=False))
    print()
    print("Endi 'python main.py' ishga tushiring - ViewTransformer")
    print("avtomatik shu kalibrlashni yuklaydi.")


if __name__ == '__main__':
    main()

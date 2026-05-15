"""
YOLO Detection Tester
=====================
Yangi videolarga to'liq pipeline'ni ishga tushirishdan oldin
shu skriptni ishlating - u faqat YOLO detectionni tekshiradi va
har bir freym uchun nima topganini ko'rsatadi.

Foydalanish:
    python tools/yolo_test.py
    python tools/yolo_test.py --video input_videos/my_video.mp4
    python tools/yolo_test.py --frames 50 --conf 0.25

Natija:
    output_videos/yolo_test/ - har 30-freymdan annotatsiyalangan
                               surat va konsolda statistika
"""
import argparse
import os
import sys
import time
from collections import Counter

import cv2
import numpy as np


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--video', default='input_videos/08fd33_4.mp4',
                        help='Test qilinadigan video yo\'li')
    parser.add_argument('--model', default='models/best.pt',
                        help='YOLO model yo\'li')
    parser.add_argument('--frames', type=int, default=20,
                        help='Nechta freymni sinash (default: 20)')
    parser.add_argument('--every', type=int, default=30,
                        help='Har N-chi freymni olish (default: 30)')
    parser.add_argument('--conf', type=float, default=0.1,
                        help='Confidence threshold (default: 0.1)')
    parser.add_argument('--output', default='output_videos/yolo_test',
                        help='Annotated suratlar saqlanadigan papka')
    args = parser.parse_args()

    # Tekshiruvlar
    if not os.path.exists(args.video):
        print(f"❌ Video topilmadi: {args.video}")
        sys.exit(1)
    if not os.path.exists(args.model):
        print(f"❌ Model topilmadi: {args.model}")
        sys.exit(1)
    os.makedirs(args.output, exist_ok=True)

    # GPU tekshiruv
    try:
        import torch
        device_info = (
            f"GPU: {torch.cuda.get_device_name(0)}"
            if torch.cuda.is_available() else "CPU (sekin bo'ladi)"
        )
        print(f"🖥️  {device_info}")
    except ImportError:
        pass

    # Model yuklash
    from ultralytics import YOLO
    print(f"📦 Model yuklanmoqda: {args.model}")
    model = YOLO(args.model)
    print(f"   Class'lar: {model.names}")

    # Video o'qish
    cap = cv2.VideoCapture(args.video)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = cap.get(cv2.CAP_PROP_FPS)
    w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"🎬 Video: {w}x{h} @ {fps:.1f} fps, {total_frames} freym "
          f"({total_frames / fps:.1f} sek)")

    # Detection
    print(f"\n🔍 Test boshlandi (har {args.every}-freym, {args.frames} ta freym)")
    print("-" * 70)

    class_counts = Counter()
    confidence_stats = {name: [] for name in model.names.values()}
    frame_idx = 0
    saved = 0
    t0 = time.time()

    while saved < args.frames:
        ret, frame = cap.read()
        if not ret:
            break

        if frame_idx % args.every == 0:
            results = model.predict(frame, conf=args.conf, verbose=False)
            r = results[0]

            # Statistika
            frame_classes = Counter()
            for box in r.boxes:
                cls_id = int(box.cls)
                conf = float(box.conf)
                class_name = model.names[cls_id]
                class_counts[class_name] += 1
                frame_classes[class_name] += 1
                confidence_stats[class_name].append(conf)

            # Annotated freymni saqlash
            annotated = r.plot()
            out_path = os.path.join(args.output, f"frame_{frame_idx:06d}.jpg")
            cv2.imwrite(out_path, annotated)

            details = ', '.join(f'{k}={v}' for k, v in frame_classes.items()) or 'hech narsa yo\'q'
            print(f"  Freym {frame_idx:>5d}: {details}")
            saved += 1

        frame_idx += 1

    cap.release()
    elapsed = time.time() - t0

    # Yakuniy hisobot
    print("-" * 70)
    print(f"\n📊 STATISTIKA ({saved} freym, {elapsed:.1f} sek):\n")
    if not class_counts:
        print("  ⚠️  Hech narsa aniqlanmadi! Mumkin sabablar:")
        print("     - confidence threshold juda yuqori (--conf 0.05 sinab ko'ring)")
        print("     - model bu turdagi videoga mos emas (fine-tuning kerak)")
        print("     - video sifati past")
        return

    for class_name, count in class_counts.most_common():
        confs = confidence_stats[class_name]
        avg_conf = np.mean(confs)
        per_frame = count / saved
        marker = "✅" if avg_conf > 0.5 else "⚠️ " if avg_conf > 0.25 else "❌"
        print(f"  {marker} {class_name:12s}: {count:4d} ta jami | "
              f"{per_frame:5.1f} freym/o'rta | "
              f"o'rtacha conf: {avg_conf:.2f}")

    print(f"\n💾 Annotated suratlar: {args.output}/")
    print("\n💡 Tavsiya:")
    if class_counts.get('player', 0) / max(saved, 1) < 5:
        print("   - O'yinchilar kam aniqlanyapti (har freymda <5 ta).")
        print("     Bu kamera burchagi past yoki video sifati past degani.")
    if class_counts.get('ball', 0) / max(saved, 1) < 0.3:
        print("   - To'p kam aniqlanyapti. Bu eng qiyin obyekt.")
        print("     Pipeline'da interpolatsiya yordam beradi, lekin sifat past bo'lsa - fine-tuning kerak.")

    # O'rtacha confidence tahlili
    all_confs = [c for confs in confidence_stats.values() for c in confs]
    if all_confs:
        avg_all = np.mean(all_confs)
        if avg_all > 0.6:
            print("   ✅ Confidence yuqori - model sizning videongizga yaxshi mos.")
        elif avg_all > 0.35:
            print("   ⚠️  Confidence o'rtacha - ishlaydi, lekin fine-tuning sifatni oshiradi.")
        else:
            print("   ❌ Confidence past - fine-tuning shart deb hisoblang.")


if __name__ == '__main__':
    main()

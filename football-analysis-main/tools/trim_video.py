"""
Video Trimmer
=============
Uzun videolardan qisqa fragment kesib olish (ffmpeg orqali).
RAM ko'p ishlatadigan pipeline uchun video qisqa bo'lishi muhim.

Foydalanish:
    python tools/trim_video.py --input input_videos/full.mp4 \\
        --output input_videos/trimmed.mp4 --start 0 --duration 30

    # 1 daqiqadan boshlab 45 sekund:
    python tools/trim_video.py -i full.mp4 -o short.mp4 -s 60 -d 45

Eslatma: ffmpeg o'rnatilgan bo'lishi kerak (apt-get install ffmpeg).
"""
import argparse
import os
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-i', '--input', required=True, help='Asl video')
    parser.add_argument('-o', '--output', required=True, help='Kesilgan video')
    parser.add_argument('-s', '--start', type=float, default=0,
                        help='Boshlanish vaqti (sekund), default: 0')
    parser.add_argument('-d', '--duration', type=float, default=30,
                        help='Davomiylik (sekund), default: 30')
    parser.add_argument('--reencode', action='store_true',
                        help='Qayta encode qilish (sekinroq, lekin aniq kesadi)')
    args = parser.parse_args()

    if not os.path.exists(args.input):
        print(f"❌ Topilmadi: {args.input}")
        sys.exit(1)

    # ffmpeg mavjudligini tekshirish
    if subprocess.run(['which', 'ffmpeg'], capture_output=True).returncode != 0:
        print("❌ ffmpeg topilmadi. O'rnatish: apt-get install ffmpeg")
        sys.exit(1)

    os.makedirs(os.path.dirname(os.path.abspath(args.output)) or '.', exist_ok=True)

    cmd = ['ffmpeg', '-y', '-ss', str(args.start), '-i', args.input,
           '-t', str(args.duration)]
    if args.reencode:
        cmd += ['-c:v', 'libx264', '-preset', 'fast', '-crf', '23']
    else:
        cmd += ['-c', 'copy']  # tezroq, codec'siz
    cmd.append(args.output)

    print(f"✂️  Kesilmoqda: {args.start}s dan {args.start + args.duration}s gacha")
    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0:
        print("❌ ffmpeg xato berdi:")
        print(result.stderr[-500:])
        print("\n💡 --reencode flagini sinab ko'ring (aniqroq kesadi)")
        sys.exit(1)

    size_mb = os.path.getsize(args.output) / 1e6
    print(f"✅ Saqlandi: {args.output} ({size_mb:.1f} MB)")


if __name__ == '__main__':
    main()

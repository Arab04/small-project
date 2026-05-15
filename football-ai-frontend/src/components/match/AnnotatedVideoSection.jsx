import { Download, Play, Film } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { storageApi } from '@/api/video';

/**
 * Tahlil qilingan video — annotated video player + yuklab olish tugmasi.
 *
 * result.annotated_video_path — MinIO key (masalan: analytics/uuid/annotated_video.mp4)
 */
export function AnnotatedVideoSection({ result }) {
  const videoPath = result?.annotated_video_path || result?.annotatedVideoPath;
  if (!videoPath) return null;

  const videoUrl = storageApi.getVideoUrl(videoPath);
  const downloadUrl = storageApi.getVideoDownloadUrl(videoPath);

  return (
    <div className="border-b border-hairline border-white/[0.06]">
      <div className="px-6 py-5">
        <div className="flex items-end justify-between flex-wrap gap-4">
          <div>
            <div className="text-2xs text-ink-300 font-medium tracking-wider uppercase mb-1">
              AI tomonidan tahlil qilingan
            </div>
            <div className="text-[22px] font-semibold tracking-tight flex items-center gap-2">
              <Film className="w-5 h-5 text-lime-electric" strokeWidth={1.6} />
              Tahlil videosi
            </div>
          </div>
          <a
            href={downloadUrl}
            download="annotated_video.mp4"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium
                       bg-lime-electric/10 text-lime-electric border border-lime-electric/20
                       hover:bg-lime-electric/20 transition-all"
          >
            <Download className="w-4 h-4" strokeWidth={1.8} />
            Yuklab olish
          </a>
        </div>
      </div>

      <div className="px-6 pb-6">
        <div className="rounded-xl overflow-hidden bg-ink-800 border border-white/[0.06]">
          <video
            controls
            preload="metadata"
            className="w-full block"
            style={{ maxHeight: '540px', background: '#000' }}
          >
            <source src={videoUrl} type="video/mp4" />
            Brauzeringiz video formatini qo'llab-quvvatlamaydi.
          </video>
        </div>

        <div className="flex items-center gap-4 mt-3 text-2xs text-ink-300">
          <div className="flex items-center gap-1.5">
            <Play className="w-3 h-3" strokeWidth={1.6} />
            <span>O'yinchilar va to'p real-time tracking bilan belgilangan</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-lime-electric/60" />
            <span>Uy jamoasi</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-sky-400/60" />
            <span>Mehmon jamoasi</span>
          </div>
        </div>
      </div>
    </div>
  );
}

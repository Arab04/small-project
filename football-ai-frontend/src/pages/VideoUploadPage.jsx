import { useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Upload, X, FileVideo, ArrowRight, ArrowLeft, RefreshCcw, Play } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { matchesApi } from '@/api/matches';
import { videoApi, analysisApi } from '@/api/video';
import { formatBytes } from '@/lib/utils';

/**
 * Video upload + calibration UI.
 *
 * 3 bosqich:
 *   1. Upload     — drag-and-drop yoki "Browse" file picker
 *   2. Calibrate  — birinchi freym ustida 4 ta nuqta bosish
 *   3. Confirm    — boshlash tugmasi
 */
export function VideoUploadPage() {
  const { matchId } = useParams();
  const navigate = useNavigate();

  const { data: match } = useQuery({
    queryKey: ['match', matchId],
    queryFn: () => matchesApi.getById(matchId),
  });

  const [step, setStep] = useState('upload'); // upload | calibrate | confirm
  const [file, setFile] = useState(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [uploadedKey, setUploadedKey] = useState(null);
  const [firstFramePreview, setFirstFramePreview] = useState(null);
  const [calibrationPoints, setCalibrationPoints] = useState({});
  const [activePoint, setActivePoint] = useState('top_left');
  const [analyzing, setAnalyzing] = useState(false);

  const fileInputRef = useRef(null);
  const videoRef = useRef(null);

  const handleFileSelect = (selectedFile) => {
    if (!selectedFile) return;
    if (!selectedFile.type.startsWith('video/')) {
      alert("Faqat video fayllar qabul qilinadi");
      return;
    }
    if (selectedFile.size > 500 * 1024 * 1024) {
      alert("Fayl 500 MB dan kichik bo'lishi kerak (5-10 daqiqalik klip)");
      return;
    }
    setFile(selectedFile);

    // Generate preview from first frame
    const url = URL.createObjectURL(selectedFile);
    const video = document.createElement('video');
    video.src = url;
    video.crossOrigin = 'anonymous';
    video.muted = true;
    video.addEventListener('loadeddata', () => {
      video.currentTime = 1;
    });
    video.addEventListener('seeked', () => {
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(video, 0, 0);
      setFirstFramePreview(canvas.toDataURL('image/jpeg', 0.8));
      URL.revokeObjectURL(url);
    });
  };

  const handleUpload = async () => {
    if (!file) return;
    setUploading(true);
    setUploadProgress(0);
    try {
      const result = await videoApi.upload(matchId, file, (pct) => {
        setUploadProgress(pct);
      });
      setUploadedKey(result?.minioKey || result?.videoUrl || true);
      setStep('calibrate');
    } catch (e) {
      alert("Upload xatosi: " + (e.response?.data?.message || e.message));
    } finally {
      setUploading(false);
    }
  };

  const handleStartAnalysis = async (useCalibration) => {
    setAnalyzing(true);
    try {
      if (useCalibration && Object.keys(calibrationPoints).length === 4) {
        const points = Object.fromEntries(
          Object.entries(calibrationPoints).map(([k, v]) => [k, [v.x, v.y]])
        );
        await analysisApi.startWithCalibration(matchId, points);
      } else {
        await analysisApi.startLongForm(matchId);
      }
      navigate(`/matches/${matchId}`);
    } catch (e) {
      alert("Tahlil boshlashda xato: " + (e.response?.data?.message || e.message));
      setAnalyzing(false);
    }
  };

  return (
    <div className="min-h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-hairline border-white/[0.06] flex items-center justify-between">
        <Button
          variant="ghost"
          size="sm"
          icon={<ArrowLeft className="w-3.5 h-3.5" />}
          onClick={() => navigate(`/matches/${matchId}`)}
        >
          Orqaga
        </Button>
        <div className="text-sm text-ink-200">
          {match?.ourTeamName || match?.homeTeam?.name || 'Uy'} vs {match?.opponentName || match?.awayTeam?.name || match?.opponent?.name || 'Mehmon'}
        </div>
        <StepIndicator step={step} />
      </div>

      {/* Step content */}
      <div className="px-6 py-8">
        {step === 'upload' && (
          <UploadStep
            file={file}
            uploadProgress={uploadProgress}
            uploading={uploading}
            onFileSelect={handleFileSelect}
            onUpload={handleUpload}
            onClear={() => { setFile(null); setFirstFramePreview(null); }}
            inputRef={fileInputRef}
          />
        )}

        {step === 'calibrate' && (
          <CalibrateStep
            firstFrame={firstFramePreview}
            points={calibrationPoints}
            setPoints={setCalibrationPoints}
            activePoint={activePoint}
            setActivePoint={setActivePoint}
            onSkip={() => handleStartAnalysis(false)}
            onContinue={() => setStep('confirm')}
            analyzing={analyzing}
          />
        )}

        {step === 'confirm' && (
          <ConfirmStep
            firstFrame={firstFramePreview}
            points={calibrationPoints}
            onBack={() => setStep('calibrate')}
            onStart={() => handleStartAnalysis(true)}
            analyzing={analyzing}
          />
        )}
      </div>
    </div>
  );
}

function StepIndicator({ step }) {
  const steps = ['upload', 'calibrate', 'confirm'];
  const labels = ['Yuklash', 'Kalibrlash', 'Boshlash'];
  const idx = steps.indexOf(step);

  return (
    <div className="flex items-center gap-2">
      {steps.map((s, i) => (
        <div key={s} className="flex items-center gap-2">
          <div
            className={`w-5 h-5 rounded-full flex items-center justify-center text-2xs font-semibold tabular ${
              i <= idx
                ? 'bg-lime-electric text-ink-900'
                : 'bg-ink-700 text-ink-300'
            }`}
          >
            {i + 1}
          </div>
          <span
            className={`text-2xs font-medium ${
              i === idx ? 'text-lime-electric'
              : i < idx ? 'text-ink-100'
              : 'text-ink-300'
            }`}
          >
            {labels[i]}
          </span>
          {i < steps.length - 1 && <div className="w-4 h-px bg-ink-600" />}
        </div>
      ))}
    </div>
  );
}

function UploadStep({ file, uploadProgress, uploading, onFileSelect, onUpload, onClear, inputRef }) {
  const [isDragging, setIsDragging] = useState(false);

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const f = e.dataTransfer.files[0];
    if (f) onFileSelect(f);
  };

  return (
    <div className="max-w-3xl mx-auto">
      <div className="mb-6">
        <div className="text-xl font-semibold tracking-tight mb-1">Video yuklash</div>
        <div className="text-sm text-ink-300">
          O'yin videoini yuklang (MP4, AVI, MKV — 500 MB gacha, 5-10 daqiqalik klip)
        </div>
      </div>

      {!file ? (
        <div
          onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={handleDrop}
          onClick={() => inputRef.current?.click()}
          className={`card border-dashed cursor-pointer transition-all ${
            isDragging ? 'border-lime-electric bg-lime-electric/5' : 'hover:border-white/[0.12]'
          }`}
          style={{ borderStyle: 'dashed', borderWidth: '1px' }}
        >
          <div className="p-12 text-center">
            <div className="w-14 h-14 mx-auto rounded-2xl bg-lime-electric/10 flex items-center justify-center mb-4">
              <Upload className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />
            </div>
            <div className="text-base font-medium mb-1">
              Video faylni shu yerga sudrab tushiring
            </div>
            <div className="text-sm text-ink-300 mb-5">
              yoki <span className="text-lime-electric">tanlash uchun bosing</span>
            </div>
            <div className="flex gap-2 justify-center">
              <Badge variant="neutral">MP4</Badge>
              <Badge variant="neutral">AVI</Badge>
              <Badge variant="neutral">MKV</Badge>
              <Badge variant="neutral">500 MB max</Badge>
            </div>
          </div>
          <input
            ref={inputRef}
            type="file"
            accept="video/*"
            className="hidden"
            onChange={(e) => onFileSelect(e.target.files[0])}
          />
        </div>
      ) : (
        <div className="card p-5">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-lime-electric/10 flex items-center justify-center shrink-0">
              <FileVideo className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium truncate">{file.name}</div>
              <div className="text-2xs text-ink-300 mt-0.5">
                {formatBytes(file.size)} · {file.type}
              </div>
            </div>
            {!uploading && (
              <button
                onClick={onClear}
                className="w-8 h-8 rounded-md hover:bg-white/[0.05] flex items-center justify-center text-ink-300"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {uploading && (
            <div className="mt-4">
              <div className="flex justify-between text-2xs text-ink-300 mb-1.5 tabular">
                <span>Yuklash...</span>
                <span>{uploadProgress}%</span>
              </div>
              <div className="h-1.5 bg-ink-700 rounded-full overflow-hidden">
                <div
                  className="h-full bg-lime-electric transition-all"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            </div>
          )}

          {!uploading && (
            <div className="mt-5 flex justify-end">
              <Button variant="primary" icon={<ArrowRight className="w-4 h-4" />} onClick={onUpload}>
                Yuklash va davom etish
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

const POINT_ORDER = ['top_left', 'top_right', 'bottom_right', 'bottom_left'];
const POINT_LABELS = {
  top_left: 'Yuqori chap (P1)',
  top_right: "Yuqori o'ng (P2)",
  bottom_right: "Pastki o'ng (P3)",
  bottom_left: 'Pastki chap (P4)',
};

function CalibrateStep({ firstFrame, points, setPoints, activePoint, setActivePoint, onSkip, onContinue, analyzing }) {
  const imageRef = useRef(null);

  const handleClick = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const naturalW = imageRef.current?.naturalWidth || rect.width;
    const naturalH = imageRef.current?.naturalHeight || rect.height;
    const x = Math.round(((e.clientX - rect.left) / rect.width) * naturalW);
    const y = Math.round(((e.clientY - rect.top) / rect.height) * naturalH);

    setPoints({ ...points, [activePoint]: { x, y } });

    // Avtomatik keyingi nuqtaga o'tish
    const idx = POINT_ORDER.indexOf(activePoint);
    if (idx < POINT_ORDER.length - 1) {
      setActivePoint(POINT_ORDER[idx + 1]);
    }
  };

  const allPointsSet = POINT_ORDER.every((p) => points[p]);

  return (
    <div className="max-w-5xl mx-auto">
      <div className="mb-6 flex items-end justify-between">
        <div>
          <div className="text-xl font-semibold tracking-tight mb-1">Maydon kalibrlash</div>
          <div className="text-sm text-ink-300">
            Maydon burchaklarini ko'rsating. Tartib bilan: yuqori chap → yuqori o'ng → pastki o'ng → pastki chap
          </div>
        </div>
        <Button variant="ghost" size="sm" onClick={onSkip} loading={analyzing}>
          O'tkazib yuborish (avto)
        </Button>
      </div>

      <div className="grid grid-cols-[1fr_280px] gap-5">
        {/* Image with click overlay */}
        <div className="card p-3">
          <div className="relative rounded-md overflow-hidden bg-ink-950" onClick={handleClick} style={{ cursor: 'crosshair' }}>
            {firstFrame ? (
              <img ref={imageRef} src={firstFrame} alt="First frame" className="w-full h-auto block select-none" draggable={false} />
            ) : (
              <div className="aspect-video shimmer" />
            )}

            {/* Point markers */}
            {Object.entries(points).map(([key, pt]) => {
              if (!imageRef.current) return null;
              const naturalW = imageRef.current.naturalWidth;
              const naturalH = imageRef.current.naturalHeight;
              const left = (pt.x / naturalW) * 100;
              const top = (pt.y / naturalH) * 100;
              const idx = POINT_ORDER.indexOf(key) + 1;
              const isActive = key === activePoint;

              return (
                <div
                  key={key}
                  className="absolute"
                  style={{
                    left: `${left}%`,
                    top: `${top}%`,
                    transform: 'translate(-50%, -50%)',
                  }}
                >
                  {isActive && (
                    <div className="absolute inset-0 -m-3 rounded-full bg-lime-electric/20 animate-ping" />
                  )}
                  <div
                    className={`relative w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs ${
                      isActive
                        ? 'bg-lime-electric text-ink-900 ring-2 ring-lime-electric/40'
                        : 'bg-lime-electric text-ink-900'
                    }`}
                  >
                    P{idx}
                  </div>
                </div>
              );
            })}
          </div>
          <div className="mt-3 text-2xs text-ink-300 px-1">
            💡 Maydon chiziqlari aniq ko'rinadigan freymdan foydalanamiz. Bo'rttirish uchun rasm ustiga bosing.
          </div>
        </div>

        {/* Right sidebar */}
        <div className="flex flex-col gap-3">
          <div className="card p-4">
            <div className="text-2xs text-ink-300 tracking-wider mb-3">KALIBRLASH NUQTALARI</div>
            <div className="flex flex-col gap-1.5">
              {POINT_ORDER.map((key, i) => {
                const isActive = key === activePoint;
                const isDone = !!points[key];
                return (
                  <button
                    key={key}
                    onClick={() => setActivePoint(key)}
                    className={`flex items-center gap-2.5 px-2.5 py-2 rounded-md text-left transition-colors ${
                      isActive ? 'bg-lime-electric/10 ring-1 ring-lime-electric/30' : 'hover:bg-white/[0.04]'
                    }`}
                  >
                    <div
                      className={`w-6 h-6 rounded-full flex items-center justify-center font-bold text-2xs shrink-0 ${
                        isDone
                          ? 'bg-lime-electric text-ink-900'
                          : isActive
                          ? 'bg-lime-electric/20 text-lime-electric ring-2 ring-lime-electric/40'
                          : 'bg-ink-700 text-ink-300'
                      }`}
                    >
                      P{i + 1}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-xs font-medium truncate">{POINT_LABELS[key]}</div>
                      {isDone && (
                        <div className="text-2xs text-ink-300 font-mono tabular">
                          x={points[key].x}, y={points[key].y}
                        </div>
                      )}
                    </div>
                    {isDone && <span className="text-lime-electric text-xs">✓</span>}
                  </button>
                );
              })}
            </div>

            {Object.keys(points).length > 0 && (
              <button
                onClick={() => { setPoints({}); setActivePoint(POINT_ORDER[0]); }}
                className="mt-3 flex items-center gap-1.5 text-2xs text-ink-300 hover:text-coral"
              >
                <RefreshCcw className="w-3 h-3" />
                Hammasini tozalash
              </button>
            )}
          </div>

          <div className="card p-4 bg-lime-electric/5">
            <div className="text-2xs text-lime-electric tracking-wider mb-2 font-semibold">NEGA KERAK?</div>
            <div className="text-xs text-ink-100 leading-relaxed">
              Calibration aniq bo'lsa — heatmap, masofa va tezlik haqiqiy metrlarda hisoblanadi.
              Avtomatik kalibrlash 80% holatda ishlaydi, lekin qo'lda aniqroq.
            </div>
          </div>

          <div className="flex gap-2 mt-2">
            <Button variant="ghost" onClick={onSkip} className="flex-1" loading={analyzing}>
              O'tkazib yuborish
            </Button>
            <Button
              variant="primary"
              icon={<ArrowRight className="w-4 h-4" />}
              disabled={!allPointsSet}
              onClick={onContinue}
              className="flex-1"
            >
              Davom etish
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

function ConfirmStep({ firstFrame, points, onBack, onStart, analyzing }) {
  return (
    <div className="max-w-2xl mx-auto">
      <div className="mb-6">
        <div className="text-xl font-semibold tracking-tight mb-1">Tahlilni boshlash</div>
        <div className="text-sm text-ink-300">
          Hammasi tayyor. Tahlil 25-35 daqiqa davom etadi.
        </div>
      </div>

      <div className="card p-5 mb-4">
        <div className="text-2xs text-ink-300 tracking-wider mb-2">KALIBRLASH</div>
        <div className="grid grid-cols-2 gap-2">
          {Object.entries(points).map(([key, pt], i) => (
            <div key={key} className="flex items-center gap-2 px-2.5 py-2 bg-white/[0.03] rounded">
              <div className="w-5 h-5 rounded-full bg-lime-electric flex items-center justify-center text-2xs font-bold text-ink-900">
                P{i + 1}
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-2xs font-mono tabular text-ink-200">
                  ({pt.x}, {pt.y})
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="card p-5 bg-lime-electric/5 border-lime-electric/15 mb-5">
        <div className="text-2xs text-lime-electric tracking-wider mb-2 font-semibold">⚡ TAHLIL TARKIBI</div>
        <div className="text-sm text-ink-100 leading-relaxed">
          O'yinchi tracking, possession, sprint va tezlik, heatmap va pass network,
          jamoa taqqoslash hamda Claude AI insights tayyorlanadi.
        </div>
      </div>

      <div className="flex gap-2">
        <Button variant="ghost" icon={<ArrowLeft className="w-4 h-4" />} onClick={onBack}>
          Orqaga
        </Button>
        <Button
          variant="primary"
          icon={<Play className="w-4 h-4" fill="currentColor" />}
          onClick={onStart}
          loading={analyzing}
          className="flex-1"
        >
          Tahlilni boshlash
        </Button>
      </div>
    </div>
  );
}

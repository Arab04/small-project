import { api, unwrap } from './client';

/**
 * Video Analysis API — Spring Boot ↔ ML servis (RunPod).
 *
 * Workflow:
 *   1. uploadVideo(matchId, file)  → MinIO ga yuklanadi
 *   2. analysisApi.start() → ML jobni boshlash
 *   3. getJobStatus(jobId) — har 2-3 sek pollar
 *   4. getResult(jobId) — tugagandan keyin to'liq natija
 */
export const videoApi = {
  /**
   * Video yuklash. Qisqa videolar (5-10 daqiqa, max 500 MB) uchun mo'ljallangan.
   * Progress callback bilan progress bar ko'rsatiladi.
   */
  upload: async (matchId, file, onProgress) => {
    const formData = new FormData();
    formData.append('file', file);

    const res = await api.post(`/matches/${matchId}/video`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        if (e.total && onProgress) {
          const pct = Math.round((e.loaded * 100) / e.total);
          onProgress(pct, e.loaded, e.total);
        }
      },
      // Progress chiroyli ko'rinishi uchun timeout uzun
      timeout: 60 * 60 * 1000, // 1 soat
    });
    return unwrap(res);
  },
};

export const analysisApi = {
  // Avtomatik kalibrlash bilan
  start: async (matchId) => {
    const res = await api.post(`/matches/${matchId}/analysis/start`);
    return unwrap(res);
  },

  // Qo'lda kalibrlash bilan
  startWithCalibration: async (matchId, calibrationPoints) => {
    const res = await api.post(
      `/matches/${matchId}/analysis/start-with-calibration`,
      calibrationPoints
    );
    return unwrap(res);
  },

  // Qisqa videolar uchun standart tahlil (5-10 min klipler)
  // Backward compat uchun startLongForm nomi ham qoldi (alias)
  startLongForm: async (matchId, calibrationPoints = null) => {
    const res = await api.post(
      `/matches/${matchId}/analysis/start-long-form`,
      calibrationPoints || {}
    );
    return unwrap(res);
  },

  // Crash bo'lgan jobni qaytadan boshlash
  resume: async (matchId, jobId) => {
    const res = await api.post(`/matches/${matchId}/analysis/jobs/${jobId}/resume`);
    return unwrap(res);
  },

  // Jobni bekor qilish
  cancel: async (matchId, jobId) => {
    const res = await api.post(`/matches/${matchId}/analysis/jobs/${jobId}/cancel`);
    return unwrap(res);
  },

  // Job statusi (real-time progress)
  getJobStatus: async (matchId, jobId) => {
    const res = await api.get(`/matches/${matchId}/analysis/jobs/${jobId}`);
    return unwrap(res);
  },

  // Match'ning barcha tahlil tarixi
  listJobs: async (matchId) => {
    const res = await api.get(`/matches/${matchId}/analysis/jobs`);
    return unwrap(res);
  },

  // Tugagan tahlilning to'liq natijasi (timeline, periods, stamina, heatmap keys)
  getResult: async (matchId, jobId) => {
    const res = await api.get(`/matches/${matchId}/analysis/jobs/${jobId}/result`);
    const raw = unwrap(res);
    // Raw JSON string bo'lsa - parse qilish
    return typeof raw === 'string' ? JSON.parse(raw) : raw;
  },
};

/**
 * Heatmap rasm URL — MinIO key'dan to'liq URLga.
 *
 * Spring Boot endpoint: GET /api/storage/image?key=...
 * Pre-signed MinIO URL qaytaradi yoki proxy orqali ko'rsatadi.
 */
export const storageApi = {
  getImageUrl: (minioKey) => {
    if (!minioKey) return null;
    return `/api/storage/image?key=${encodeURIComponent(minioKey)}`;
  },

  getVideoUrl: (minioKey) => {
    if (!minioKey) return null;
    return `/api/storage/video?key=${encodeURIComponent(minioKey)}`;
  },

  getVideoDownloadUrl: (minioKey) => {
    if (!minioKey) return null;
    return `/api/storage/video/download?key=${encodeURIComponent(minioKey)}`;
  },
};

/**
 * Claude AI taktik tahlil
 */
export const claudeApi = {
  ask: async (matchId, question) => {
    const res = await api.post('/analysis/request', { matchId, question });
    return unwrap(res);
  },
};

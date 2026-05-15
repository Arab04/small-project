/**
 * Football AI — Frontend enhancements
 * Annotated video player + download tugmasi
 * Natija tarixini saqlash
 */
(function() {
  'use strict';

  const API_BASE = '/api';
  const STORAGE_KEY = 'football_ai_analysis_history';

  // DOM o'zgarishlarini kuzatish — React content yuklangandan keyin ishlash
  const observer = new MutationObserver(function() {
    injectVideoPlayer();
    injectDownloadButton();
  });
  observer.observe(document.body, { childList: true, subtree: true });

  // Har 2 sekundda tekshirish (MutationObserver'dan tashqari)
  setInterval(function() {
    injectVideoPlayer();
    injectDownloadButton();
  }, 2000);

  // ===== Annotated Video Player =====
  function injectVideoPlayer() {
    // Agar allaqachon inject qilingan bo'lsa, o'tkazib yuboramiz
    if (document.getElementById('ai-video-player-container')) return;

    // Heatmap rasmni topamiz — uning yoniga video player qo'shamiz
    const heatmapImages = document.querySelectorAll('img[alt*="heatmap"]');
    if (heatmapImages.length === 0) return;

    // Natija JSON'ni sahifadan olish
    const resultData = getResultData();
    if (!resultData) return;

    const videoPath = resultData.annotated_video_path;
    if (!videoPath) return;

    const videoUrl = `${API_BASE}/storage/video?key=${encodeURIComponent(videoPath)}`;
    const downloadUrl = `${API_BASE}/storage/video/download?key=${encodeURIComponent(videoPath)}`;

    // Video player container yaratish
    const container = document.createElement('div');
    container.id = 'ai-video-player-container';
    container.style.cssText = `
      margin: 16px 0;
      padding: 16px;
      background: rgba(15, 15, 20, 0.8);
      border-radius: 12px;
      border: 1px solid rgba(197, 255, 80, 0.2);
    `;
    container.innerHTML = `
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
        <h3 style="color: #c5ff50; font-size: 14px; font-weight: 600; letter-spacing: 1px; margin: 0;">
          🎬 TAHLIL QILINGAN VIDEO
        </h3>
        <a href="${downloadUrl}"
           download="annotated_video.mp4"
           style="display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px;
                  background: rgba(197, 255, 80, 0.15); color: #c5ff50; border-radius: 8px;
                  font-size: 12px; font-weight: 500; text-decoration: none; border: 1px solid rgba(197, 255, 80, 0.3);
                  cursor: pointer; transition: all 0.2s;">
          ⬇️ Yuklab olish
        </a>
      </div>
      <video controls preload="metadata"
             style="width: 100%; border-radius: 8px; background: #000;"
             poster="">
        <source src="${videoUrl}" type="video/mp4">
        Brauzeringiz video formatini qo'llab-quvvatlamaydi.
      </video>
    `;

    // Heatmap'ning parent container'idan oldin qo'shamiz
    const heatmapSection = heatmapImages[0].closest('div[class*="rounded"]')
                          || heatmapImages[0].parentElement;
    const parentSection = heatmapSection.parentElement;
    if (parentSection) {
      parentSection.insertBefore(container, parentSection.firstChild);
    }

    // Natijani tarixga saqlash
    saveToHistory(resultData);
  }

  // ===== Download Button (Alohida) =====
  function injectDownloadButton() {
    if (document.getElementById('ai-download-btn')) return;

    const resultData = getResultData();
    if (!resultData || !resultData.annotated_video_path) return;

    // Sahifada "TAHLIL" yoki statistika bo'limi bormi tekshirish
    const statsHeaders = document.querySelectorAll('h2, h3, [class*="header"]');
    let targetHeader = null;
    for (const h of statsHeaders) {
      if (h.textContent && (h.textContent.includes('TAHLIL') || h.textContent.includes('Natija'))) {
        targetHeader = h;
        break;
      }
    }
    if (!targetHeader) return;

    const downloadUrl = `${API_BASE}/storage/video/download?key=${encodeURIComponent(resultData.annotated_video_path)}`;

    const btn = document.createElement('a');
    btn.id = 'ai-download-btn';
    btn.href = downloadUrl;
    btn.download = 'annotated_video.mp4';
    btn.style.cssText = `
      display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px;
      background: linear-gradient(135deg, rgba(197, 255, 80, 0.2), rgba(107, 180, 255, 0.2));
      color: #c5ff50; border-radius: 8px; font-size: 13px; font-weight: 600;
      text-decoration: none; border: 1px solid rgba(197, 255, 80, 0.4);
      cursor: pointer; margin-left: 12px; transition: all 0.2s;
    `;
    btn.textContent = '📥 Video yuklab olish';
    btn.onmouseover = () => btn.style.background = 'rgba(197, 255, 80, 0.3)';
    btn.onmouseout = () => btn.style.background = 'linear-gradient(135deg, rgba(197, 255, 80, 0.2), rgba(107, 180, 255, 0.2))';

    targetHeader.parentElement.appendChild(btn);
  }

  // ===== Natijalarni localStorage'ga saqlash =====
  function saveToHistory(resultData) {
    try {
      const history = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      const entry = {
        jobId: resultData.job_id,
        matchId: resultData.match_id,
        timestamp: new Date().toISOString(),
        homeTeam: resultData.home_team_name || 'Team A',
        awayTeam: resultData.away_team_name || 'Team B',
        totalFrames: resultData.total_frames_processed,
        videoPath: resultData.annotated_video_path,
        possession: {
          home: resultData.raw_analytics?.possession?.team_1?.possession_pct,
          away: resultData.raw_analytics?.possession?.team_2?.possession_pct,
        },
        maxSpeed: {
          home: resultData.raw_analytics?.speed?.team_1?.max_speed_kmh,
          away: resultData.raw_analytics?.speed?.team_2?.max_speed_kmh,
        },
      };

      // Dublikatni tekshirish
      const exists = history.findIndex(h => h.jobId === entry.jobId);
      if (exists >= 0) {
        history[exists] = entry; // Yangilash
      } else {
        history.unshift(entry); // Boshiga qo'shish
      }

      // Oxirgi 50 ta saqlash
      if (history.length > 50) history.length = 50;

      localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
    } catch (e) {
      console.warn('Tarix saqlashda xato:', e);
    }
  }

  // ===== Natija ma'lumotini olish =====
  function getResultData() {
    // window.__ANALYSIS_RESULT__ orqali (agar biz qo'ysak)
    if (window.__ANALYSIS_RESULT__) return window.__ANALYSIS_RESULT__;

    // URL'dan match va job ID olish va API'dan so'rash
    const match = window.location.pathname.match(/matches\/([a-f0-9-]+)/);
    if (!match) return null;

    const matchId = match[1];
    const cacheKey = `_result_${matchId}`;

    // Cache'dan olish
    if (window[cacheKey]) return window[cacheKey];

    // API'dan async olish
    fetchResult(matchId, cacheKey);
    return null;
  }

  async function fetchResult(matchId, cacheKey) {
    try {
      // Token olish
      const token = localStorage.getItem('token') ||
                    document.cookie.match(/token=([^;]+)/)?.[1] || '';

      // Barcha job'larni olish
      const jobsResp = await fetch(`${API_BASE}/matches/${matchId}/analysis/jobs`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!jobsResp.ok) return;
      const jobsData = await jobsResp.json();
      const jobs = jobsData.data || jobsData;
      if (!Array.isArray(jobs) || jobs.length === 0) return;

      // Oxirgi COMPLETED job
      const completedJob = jobs.find(j => j.status === 'COMPLETED');
      if (!completedJob) return;

      // Natijani olish
      const resultResp = await fetch(`${API_BASE}/matches/${matchId}/analysis/jobs/${completedJob.id}/result`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!resultResp.ok) return;
      const resultData = await resultResp.json();
      let result = resultData.data || resultData;

      // String bo'lsa parse qilish
      if (typeof result === 'string') {
        try { result = JSON.parse(result); } catch(e) {}
      }

      window[cacheKey] = result;
      window.__ANALYSIS_RESULT__ = result;

      // Qayta render qilish uchun trigger
      setTimeout(() => {
        injectVideoPlayer();
        injectDownloadButton();
      }, 500);
    } catch (e) {
      console.warn('Natija olishda xato:', e);
    }
  }
})();

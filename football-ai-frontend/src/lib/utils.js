import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Tailwind class merging — duplicate'larni olib tashlaydi.
 */
export function cn(...inputs) {
  return twMerge(clsx(inputs));
}

/**
 * Sekundlarni HH:MM:SS formatga.
 */
export function formatDuration(seconds) {
  if (!seconds || seconds < 0) return '0:00';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) {
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }
  return `${m}:${String(s).padStart(2, '0')}`;
}

/**
 * Daqiqani "MM'" formatga (futbol stilida).
 */
export function formatMatchMinute(seconds) {
  if (!seconds) return '0\'';
  return `${Math.floor(seconds / 60)}'`;
}

/**
 * Bytelarni human-readable formatga.
 */
export function formatBytes(bytes) {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
}

/**
 * Soniyalarni "5 daq 23 sek" formatga.
 */
export function formatRelativeDuration(seconds) {
  if (!seconds || seconds < 0) return '0 sek';
  if (seconds < 60) return `${Math.floor(seconds)} sek`;
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  if (m < 60) {
    return s > 0 ? `${m} daq ${s} sek` : `${m} daq`;
  }
  const h = Math.floor(m / 60);
  return `${h} soat ${m % 60} daq`;
}

/**
 * ISO sanani "17 Apr, 14:30" formatga.
 */
export function formatDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const months = ['Yan', 'Fev', 'Mar', 'Apr', 'May', 'Iyn', 'Iyl', 'Avg', 'Sen', 'Okt', 'Noy', 'Dek'];
  const day = d.getDate();
  const mon = months[d.getMonth()];
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${day} ${mon}, ${hh}:${mm}`;
}

/**
 * Status'ni Uzbek tiliga.
 */
export const statusLabels = {
  PENDING: 'Kutilyapti',
  DOWNLOADING: 'Yuklanmoqda',
  EXTRACTING_FRAMES: 'Freym ajratish',
  DETECTING_EVENTS: 'Eventlar topilmoqda',
  ANALYZING_PLAYERS: "O'yinchilar tahlili",
  READING_SCOREBOARD: 'Hisob o\'qilmoqda',
  DETECTING_PHASES: 'Yarmilar aniqlanmoqda',
  COMPUTING_TIMELINE: 'Timeline hisobi',
  GENERATING_REPORTS: 'Hisobot tayyorlanmoqda',
  COMPLETED: 'Tugadi',
  FAILED: 'Xato',
  CANCELLED: 'Bekor qilindi',
};

export function statusLabel(status) {
  return statusLabels[status] || status;
}

/**
 * Phase nomini Uzbek tiliga.
 */
export const phaseLabels = {
  FIRST_HALF: '1-yarim',
  HALFTIME: 'Tanaffus',
  SECOND_HALF: '2-yarim',
  EXTRA_TIME_FIRST: '1-qo\'shimcha',
  EXTRA_TIME_SECOND: '2-qo\'shimcha',
  UNKNOWN: 'Noma\'lum',
};

export function phaseLabel(phase) {
  return phaseLabels[phase] || phase || '';
}

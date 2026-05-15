import axios from 'axios';

/**
 * Axios instance — Spring Boot backend bilan bog'lanish.
 *
 * Bazaviy URL: vite.config.js da proxy qilingan
 *   dev:  /api → http://localhost:8060
 *   prod: VITE_API_URL ga
 */
export const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  // Video upload uchun ko'p vaqt (qisqa klip ham 1-3 daqiqa upload bo'lishi mumkin)
  timeout: 15 * 60 * 1000, // 15 min (qisqa klip uchun yetarli)
});

// Request interceptor — JWT token qo'shish
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('auth_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor — 401 da logout
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('auth_token');
      // Login sahifasiga yo'naltirish (agar shu sahifa bo'lmasa)
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

/**
 * Helper: API response.data.data ni olish.
 * Spring Boot quyidagi formatda javob qaytaradi:
 *   { ok: true, message: "...", data: <actual data> }
 */
export const unwrap = (response) => {
  const body = response.data;
  if (body && typeof body === 'object' && 'data' in body) {
    return body.data;
  }
  return body;
};

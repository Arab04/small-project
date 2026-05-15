import { create } from 'zustand';
import { authApi } from '@/api/auth';

/**
 * Authentication store (Zustand).
 *
 * Backend AuthResponse formati:
 *   {
 *     accessToken, refreshToken, userId, fullName, email,
 *     role, clubId, clubName
 *   }
 *
 * /users/me endpoint formati:
 *   {
 *     id, fullName, email, phoneNumber, role, clubId, clubName
 *   }
 */
export const useAuthStore = create((set, get) => ({
  user: null,
  token: localStorage.getItem('auth_token') || null,
  isLoading: false,
  error: null,

  /**
   * Sahifa yuklanganda - token bor bo'lsa, /users/me orqali userni olish.
   */
  init: async () => {
    const token = localStorage.getItem('auth_token');
    if (!token) {
      set({ user: null, token: null, isLoading: false });
      return;
    }
    set({ isLoading: true });
    try {
      const profile = await authApi.me();
      // /users/me dan kelgan formatni shape qilamiz
      const user = {
        id: profile.id,
        fullName: profile.fullName,
        email: profile.email,
        phoneNumber: profile.phoneNumber,
        role: profile.role,
        club: profile.clubId
          ? { id: profile.clubId, name: profile.clubName }
          : null,
      };
      set({ user, token, isLoading: false });
    } catch (e) {
      // Token noto'g'ri - tozalash
      localStorage.removeItem('auth_token');
      localStorage.removeItem('refresh_token');
      set({ user: null, token: null, isLoading: false });
    }
  },

  login: async (email, password) => {
    set({ isLoading: true, error: null });
    try {
      const data = await authApi.login(email, password);
      const token = data.accessToken;
      if (!token) {
        set({ isLoading: false, error: "Login muvaffaqiyatsiz: token kelmadi" });
        return false;
      }
      localStorage.setItem('auth_token', token);
      if (data.refreshToken) {
        localStorage.setItem('refresh_token', data.refreshToken);
      }
      const user = {
        id: data.userId,
        fullName: data.fullName,
        email: data.email,
        role: data.role,
        club: data.clubId ? { id: data.clubId, name: data.clubName } : null,
      };
      set({ user, token, isLoading: false, error: null });
      return true;
    } catch (e) {
      const msg = e.response?.data?.message || "Email yoki parol noto'g'ri";
      set({ isLoading: false, error: msg });
      return false;
    }
  },

  register: async (data) => {
    set({ isLoading: true, error: null });
    try {
      const response = await authApi.register(data);
      const token = response.accessToken;
      if (!token) {
        set({ isLoading: false, error: "Ro'yxatdan o'tishda xato" });
        return false;
      }
      localStorage.setItem('auth_token', token);
      if (response.refreshToken) {
        localStorage.setItem('refresh_token', response.refreshToken);
      }
      const user = {
        id: response.userId,
        fullName: response.fullName,
        email: response.email,
        role: response.role,
        club: response.clubId ? { id: response.clubId, name: response.clubName } : null,
      };
      set({ user, token, isLoading: false, error: null });
      return true;
    } catch (e) {
      const msg = e.response?.data?.message || "Ro'yxatdan o'tishda xato";
      set({ isLoading: false, error: msg });
      return false;
    }
  },

  logout: () => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('refresh_token');
    set({ user: null, token: null });
  },

  isAuthenticated: () => !!get().token || !!localStorage.getItem('auth_token'),
}));

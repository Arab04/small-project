import { api, unwrap } from './client';

/**
 * Auth API.
 * Spring Boot endpoints:
 *   POST /api/auth/login    → AuthResponse
 *   POST /api/auth/register → AuthResponse
 *   GET  /api/users/me      → user profile
 *   POST /api/auth/refresh  → AuthResponse
 */
export const authApi = {
  login: async (email, password) => {
    const res = await api.post('/auth/login', { email, password });
    return unwrap(res);
  },

  register: async (data) => {
    // data: { fullName, email, password, phoneNumber, clubName, clubCity }
    const res = await api.post('/auth/register', data);
    return unwrap(res);
  },

  me: async () => {
    const res = await api.get('/users/me');
    return unwrap(res);
  },

  refresh: async (refreshToken) => {
    const res = await api.post('/auth/refresh', { refreshToken });
    return unwrap(res);
  },
};

import { api, unwrap } from './client';

/**
 * Match va o'yinchi API.
 */
export const matchesApi = {
  // Barcha o'yinlar (paginated response → content array)
  list: async (params = {}) => {
    const res = await api.get('/matches', { params });
    const data = unwrap(res);
    // Backend PageResponse qaytaradi: { content: [...], totalPages, ... }
    if (data && Array.isArray(data.content)) return data.content;
    if (Array.isArray(data)) return data;
    return [];
  },

  // Bitta o'yin
  getById: async (id) => {
    const res = await api.get(`/matches/${id}`);
    return unwrap(res);
  },

  // Yangi o'yin yaratish
  create: async (data) => {
    const res = await api.post('/matches', data);
    return unwrap(res);
  },

  update: async (id, data) => {
    const res = await api.put(`/matches/${id}`, data);
    return unwrap(res);
  },

  delete: async (id) => {
    const res = await api.delete(`/matches/${id}`);
    return unwrap(res);
  },
};

export const teamsApi = {
  list: async () => {
    const res = await api.get('/teams');
    return unwrap(res);
  },

  getById: async (id) => {
    const res = await api.get(`/teams/${id}`);
    return unwrap(res);
  },
};

export const opponentsApi = {
  list: async () => {
    const res = await api.get('/opponents');
    return unwrap(res);
  },
};

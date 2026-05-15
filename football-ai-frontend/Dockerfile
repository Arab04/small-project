# ============================================================
# Multi-stage Dockerfile — Football AI Frontend (Vite + React + nginx)
# ============================================================

# ===== BUILD STAGE =====
FROM node:20-alpine AS build

WORKDIR /app

# Vite build vaqtida ishlatadigan API URL (compose'dan beriladi)
ARG VITE_API_URL=http://localhost:8060
ENV VITE_API_URL=${VITE_API_URL}

# Avval package.json/lock ni copy qilib install qilamiz (cache uchun)
COPY package.json package-lock.json* ./
RUN npm ci --include=dev

# Endi kodni copy qilib build qilamiz
COPY . .
RUN npm run build

# ===== RUNTIME STAGE (nginx) =====
FROM nginx:1.27-alpine

# Build qilingan static fayllarni nginx html'iga ko'chirish
COPY --from=build /app/dist /usr/share/nginx/html

# nginx config — SPA routing + /api proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost/ > /dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]

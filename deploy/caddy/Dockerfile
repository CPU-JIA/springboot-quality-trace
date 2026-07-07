FROM node:24-alpine AS frontend-build

WORKDIR /workspace/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM caddy:2-alpine

COPY deploy/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=frontend-build /workspace/frontend/dist /srv/app


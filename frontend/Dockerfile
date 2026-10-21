FROM node:25-alpine AS build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend ./
COPY docs/openapi.json /app/docs/openapi.json
COPY scripts/generate-api.mjs /app/scripts/generate-api.mjs
RUN mkdir -p /app/backend/src/main/resources/static
ARG VITE_DEMO_MODE=false
ARG VITE_HOSPITAL_TIMEZONE=Asia/Kolkata
ENV VITE_DEMO_MODE=$VITE_DEMO_MODE
ENV VITE_HOSPITAL_TIMEZONE=$VITE_HOSPITAL_TIMEZONE
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 8080

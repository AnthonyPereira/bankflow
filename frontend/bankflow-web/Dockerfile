# syntax=docker/dockerfile:1
# Image du frontend : build Angular, puis service par Nginx sans privilèges (ADR-002).

FROM node:26.11.1-alpine@sha256:143494b1da2945f061539253adc65e4f1569ddf07da2d384c022c791a9d90a4a AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --ignore-scripts
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.7-alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf nginx/hsts.conf /etc/nginx/snippets/
COPY --from=build /build/dist/bankflow-web/browser /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1

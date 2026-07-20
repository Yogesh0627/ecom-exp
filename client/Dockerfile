# syntax=docker/dockerfile:1

# ---- Dependencies ----
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---- Build ----
FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* is inlined into the client bundle at build time, so every one the app reads must be
# present NOW (Render passes service env vars as build args; only ARG-declared ones are received).
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL
ARG NEXT_PUBLIC_PAYMENTS_TEST_MODE
ENV NEXT_PUBLIC_PAYMENTS_TEST_MODE=$NEXT_PUBLIC_PAYMENTS_TEST_MODE
ARG NEXT_PUBLIC_ENABLE_DEMO_LOGIN
ENV NEXT_PUBLIC_ENABLE_DEMO_LOGIN=$NEXT_PUBLIC_ENABLE_DEMO_LOGIN
ARG NEXT_PUBLIC_DEMO_ADMIN_EMAIL
ENV NEXT_PUBLIC_DEMO_ADMIN_EMAIL=$NEXT_PUBLIC_DEMO_ADMIN_EMAIL
ARG NEXT_PUBLIC_DEMO_ADMIN_PASSWORD
ENV NEXT_PUBLIC_DEMO_ADMIN_PASSWORD=$NEXT_PUBLIC_DEMO_ADMIN_PASSWORD
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ---- Run: Next.js standalone server ----
FROM node:20-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
# Bind to all interfaces so the container is reachable; PORT is injected by the host.
ENV HOSTNAME=0.0.0.0
RUN addgroup -g 1001 nodejs && adduser -u 1001 -G nodejs -S nextjs
COPY --from=build /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]

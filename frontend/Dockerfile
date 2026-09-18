# ---------------------------------------------------------------------------
# Frontend: a static Vite build served by Nginx.
#
# Unlike the Spring services (deploy/docker/Dockerfile, which packages a jar
# Maven already built), this image does the build itself - there is no
# checked-in dist/ to copy in, and npm's own layer caching (a dependency
# install layer that only invalidates when package-lock.json changes) is
# worth more here than a separate CI build step would save.
#
# VITE_API_BASE_URL is a build arg, not a runtime env var: Vite inlines
# import.meta.env.* into the bundle at build time, so it must be the address
# the *browser* will use (the gateway's host-published port), not a
# container-network name. Pointing it at a different gateway means rebuilding
# this image, not just restarting the container.
#
#   docker build --build-arg VITE_API_BASE_URL=http://localhost:8080 -t itilms/frontend:dev frontend/
#
# (docker compose passes the same build arg from GATEWAY_HOST_PORT.)
# ---------------------------------------------------------------------------

FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine

# Never run as root (same rule as every other IT-ILMS image); nginx-unprivileged
# would be simpler but the stock image needs its pid file and listen port moved.
RUN addgroup -S itilms && adduser -S -G itilms itilms \
 && sed -i 's#/run/nginx.pid#/tmp/nginx.pid#' /etc/nginx/nginx.conf \
 && sed -i '/^http {/a \    client_body_temp_path /tmp/client_temp;\n    proxy_temp_path /tmp/proxy_temp;\n    fastcgi_temp_path /tmp/fastcgi_temp;\n    uwsgi_temp_path /tmp/uwsgi_temp;\n    scgi_temp_path /tmp/scgi_temp;' /etc/nginx/nginx.conf \
 && chown -R itilms:itilms /var/cache/nginx /etc/nginx

COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

USER itilms
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=6 --start-period=10s \
  CMD wget -q --spider http://127.0.0.1:8080/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

# Self-contained build: compiles the frontend and the Spring Boot server inside
# Docker, so `docker build` needs nothing on the host except this repository and
# Docker itself.
#
# The original two-step flow (build on the host with docker/docker-build.sh,
# then package the resulting jar) is still available in Dockerfile.prebuilt.
#
#   docker build -f docker/Dockerfile -t chat2db-fa:latest .
#
# Build from the repository root, not from docker/, so the build context
# contains both the client and the server modules.

# ---------------------------------------------------------------------------
# Stage 1 - frontend (React + Umi -> static bundle)
# ---------------------------------------------------------------------------
FROM node:22-bookworm AS frontend

# The repository pins a China-based npm mirror in .npmrc. Networks that cannot
# reach it can override the registry without editing tracked files:
#   docker build --build-arg NPM_REGISTRY=https://registry.npmjs.org/ ...
ARG NPM_REGISTRY=""
ARG APP_VERSION=5.3.0

WORKDIR /build

# The lockfile's tarball URLs end in a slash ("…npmmirror.com/uuid/-/…"), so a
# registry passed without one would splice into "…runflare.comuuid/-/…".
# Normalise to exactly one trailing slash before substituting.
COPY docker/set-npm-registry.sh /usr/local/bin/set-npm-registry
RUN chmod +x /usr/local/bin/set-npm-registry

COPY chat2db-community-client/package.json chat2db-community-client/yarn.lock chat2db-community-client/.npmrc ./
RUN set-npm-registry "$NPM_REGISTRY" && \
    yarn install --frozen-lockfile --network-timeout 600000

COPY chat2db-community-client/ ./
# The COPY above restores the tracked .npmrc and yarn.lock, so reapply the
# override before any command that may still touch the registry.
#
# `umi setup` generates src/.umi/, which tsconfig.json extends. It normally runs
# as yarn's postinstall hook, but that fired above when only package.json and
# yarn.lock existed - too early to generate anything. Run it here, now that the
# sources are in place, or the prebuild tests fail on the missing tsconfig.
RUN set-npm-registry "$NPM_REGISTRY" && \
    yarn umi setup && \
    UMI_PublicPath=/static/front/ yarn run build:web:community --app_version="${APP_VERSION}"

# ---------------------------------------------------------------------------
# Stage 2 - backend (Java 17 + Spring Boot fat jar)
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS backend

WORKDIR /build

COPY chat2db-community-server/ ./chat2db-community-server/

# Warm the dependency cache first so editing sources does not refetch the world.
# Failures here are not fatal: the package step below refetches what is missing.
RUN mvn -B -f chat2db-community-server/pom.xml \
        -pl chat2db-community-start -am \
        -Dmaven.test.skip=true \
        dependency:go-offline || true

# The server serves the SPA from static/front and renders index.html through
# Thymeleaf - the same staging docker/docker-build.sh performs on the host.
COPY --from=frontend /build/dist/ ./chat2db-community-server/chat2db-community-start/src/main/resources/static/front/
COPY --from=frontend /build/dist/index.html ./chat2db-community-server/chat2db-community-start/src/main/resources/thymeleaf/index.html

RUN mvn -B clean package \
      -Dmaven.test.skip=true \
      -Dchat2db.finalName=chat2db-community \
      -f chat2db-community-server/pom.xml \
      -pl chat2db-community-start -am && \
    test -f chat2db-community-server/chat2db-community-start/target/chat2db-community.jar && \
    test -d chat2db-community-server/chat2db-community-start/target/lib

# ---------------------------------------------------------------------------
# Stage 3 - runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre

WORKDIR /app

# tini reaps zombies and forwards signals so `docker stop` shuts the JVM down
# cleanly instead of waiting out the kill timeout.
RUN apt-get update && \
    apt-get install -y --no-install-recommends tini && \
    rm -rf /var/lib/apt/lists/*

COPY --from=backend /build/chat2db-community-server/chat2db-community-start/target/chat2db-community.jar /app/chat2db-community.jar
COPY --from=backend /build/chat2db-community-server/chat2db-community-start/target/lib/ /app/lib/
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Saved connections and the local metadata store live here. Keep it a volume so
# data survives container replacement and image upgrades.
VOLUME ["/root/.chat2db-community"]

EXPOSE 10825

ENV CHAT2DB_SERVER_PORT=10825 \
    CHAT2DB_SERVER_ADDRESS=0.0.0.0 \
    CHAT2DB_DATA_DIR=/root/.chat2db-community \
    JAVA_OPTS=""

# /dev/tcp is a bash builtin and this image's /bin/sh is dash, so the probe must
# run under bash explicitly.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${CHAT2DB_SERVER_PORT}' || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]

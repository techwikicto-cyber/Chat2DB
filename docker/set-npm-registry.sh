#!/usr/bin/env bash
#
# Point the frontend build at an alternative npm registry.
#
# The repository pins registry.npmmirror.com, which is unreachable from some
# networks. Passing --build-arg NPM_REGISTRY=<url> rewrites .npmrc and the
# lockfile's tarball URLs to that registry instead. No argument is a no-op, so
# the tracked configuration is used unchanged.
#
# The lockfile stores URLs as "<registry>/<pkg>/-/<pkg>-<ver>.tgz" where the
# registry part already carries a trailing slash. A URL passed without one would
# otherwise splice into "https://mirror.example.compkg/-/pkg-1.0.0.tgz", so the
# trailing slash is normalised to exactly one here.

set -euo pipefail

registry="${1:-}"

if [ -z "${registry}" ]; then
  echo "[registry] no override given; using the tracked .npmrc"
  exit 0
fi

# Collapse any number of trailing slashes, then add exactly one back.
normalised="${registry%"${registry##*[!/]}"}/"

printf 'registry=%s\n' "${normalised}" > .npmrc

if [ -f yarn.lock ]; then
  sed -i "s#https://registry\.npmmirror\.com/#${normalised}#g" yarn.lock
fi

echo "[registry] using ${normalised}"

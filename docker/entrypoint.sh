#!/usr/bin/env bash
#
# Container entrypoint for Chat2DB Community (headless / web mode).
#
# Its main job is the encryption key. Chat2DB encrypts stored datasource
# passwords and AI API keys with AES-256-GCM, and in headless mode it refuses to
# invent a key for itself - it must be supplied. Requiring operators to create
# one by hand on every server is the single biggest obstacle to "docker run and
# go", so this script generates one on first start and keeps it inside the data
# volume, where it lives and dies with the data it protects.
#
# Precedence, highest first:
#   1. CHAT2DB_COMMUNITY_ENCRYPTION_KEY      - key material supplied directly
#   2. CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE - path to an existing key file
#   3. auto-generated at $CHAT2DB_DATA_DIR/config/encryption.key
#
# Losing this key makes every saved password and API key undecryptable, so back
# up the data volume as a whole, and treat the key file as a secret.

set -euo pipefail

DATA_DIR="${CHAT2DB_DATA_DIR:-/root/.chat2db-community}"
SERVER_PORT="${CHAT2DB_SERVER_PORT:-10825}"
SERVER_ADDRESS="${CHAT2DB_SERVER_ADDRESS:-0.0.0.0}"
DEFAULT_KEY_FILE="${DATA_DIR}/config/encryption.key"

mkdir -p "${DATA_DIR}"

# Drop empty key variables before the JVM sees them.
#
# The server reads these with System.getenv() and treats any non-null result as
# the answer - and getenv returns "" for a variable that is set but empty, not
# null. So an empty value does not mean "unset", it means "the key is the empty
# string", and the server fails on it instead of falling through to the key
# file. Compose assigns an empty string whenever the variable is absent from
# .env, which makes this the default path rather than an edge case.
if [ -z "${CHAT2DB_COMMUNITY_ENCRYPTION_KEY:-}" ]; then
  unset CHAT2DB_COMMUNITY_ENCRYPTION_KEY
fi
if [ -z "${CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE:-}" ]; then
  unset CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE
fi

generate_key_file() {
  local key_file="$1"
  local key_dir
  key_dir="$(dirname -- "${key_file}")"

  mkdir -p "${key_dir}"
  chmod 700 "${key_dir}" 2>/dev/null || true

  # 32 random bytes, Base64-encoded, exactly as AesGcmUtil expects.
  local tmp_file
  tmp_file="$(mktemp "${key_dir}/.encryption-key-XXXXXX")"
  chmod 600 "${tmp_file}"
  head -c 32 /dev/urandom | base64 -w 0 > "${tmp_file}"
  printf '\n' >> "${tmp_file}"

  # noclobber makes the move lose harmlessly if another container raced us here.
  if mv -n "${tmp_file}" "${key_file}" 2>/dev/null && [ ! -e "${tmp_file}" ]; then
    chmod 600 "${key_file}"
    echo "[chat2db] Generated a new encryption key at ${key_file}"
    echo "[chat2db] Back up this file together with the data volume - without it,"
    echo "[chat2db] stored connection passwords and AI keys cannot be decrypted."
  else
    rm -f "${tmp_file}"
  fi
}

if [ -n "${CHAT2DB_COMMUNITY_ENCRYPTION_KEY:-}" ]; then
  echo "[chat2db] Using the encryption key from CHAT2DB_COMMUNITY_ENCRYPTION_KEY"
elif [ -n "${CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE:-}" ]; then
  if [ ! -f "${CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE}" ]; then
    echo "[chat2db] ERROR: CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE points to" >&2
    echo "[chat2db]        ${CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE}, which does not exist." >&2
    echo "[chat2db]        Mount the key file there, or unset the variable to have one" >&2
    echo "[chat2db]        generated at ${DEFAULT_KEY_FILE}." >&2
    exit 1
  fi
  echo "[chat2db] Using the encryption key file ${CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE}"
else
  if [ ! -f "${DEFAULT_KEY_FILE}" ]; then
    generate_key_file "${DEFAULT_KEY_FILE}"
  fi
  export CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE="${DEFAULT_KEY_FILE}"
fi

# Sign-in password.
#
# Handled the same way as the encryption key above, and for the same reason:
# requiring the operator to place a value correctly before the software is
# usable is a step that silently fails. A password that never arrives looks
# exactly like a deployment that wants no sign-in, and the only symptom is a
# login screen that never appears.
#
# So the container always has one. Precedence, highest first:
#   1. CHAT2DB_COMMUNITY_PASSWORD       - supplied directly
#   2. $CHAT2DB_DATA_DIR/config/password - generated here on first start
#
# The generated password is printed once, on the run that creates it. That puts
# it in the container log, which is the trade every self-hosted first-run makes:
# a discoverable credential beats an unreachable service. Change it by setting
# CHAT2DB_COMMUNITY_PASSWORD, or by editing the file and restarting.
#
# CHAT2DB_DISABLE_LOGIN=true turns the gate off entirely. Only sensible while
# the port is bound to loopback: without it, anyone who can reach the service
# has full access to every stored connection.
PASSWORD_FILE="${DATA_DIR}/config/password"

generate_password_file() {
  local password_file="$1"
  local password_dir
  password_dir="$(dirname -- "${password_file}")"

  mkdir -p "${password_dir}"
  chmod 700 "${password_dir}" 2>/dev/null || true

  # 18 random bytes as URL-safe Base64: 24 characters, no shell-hostile ones.
  local tmp_file
  tmp_file="$(mktemp "${password_dir}/.password-XXXXXX")"
  chmod 600 "${tmp_file}"
  head -c 18 /dev/urandom | base64 -w 0 | tr '+/' '-_' > "${tmp_file}"
  printf '\n' >> "${tmp_file}"

  # noclobber makes the move lose harmlessly if another container raced us here.
  if mv -n "${tmp_file}" "${password_file}" 2>/dev/null && [ ! -e "${tmp_file}" ]; then
    chmod 600 "${password_file}"
    echo "[chat2db] ============================================================"
    echo "[chat2db]  Sign-in password (generated on first start):"
    echo "[chat2db]"
    echo "[chat2db]      $(cat "${password_file}")"
    echo "[chat2db]"
    echo "[chat2db]  Stored at ${password_file} inside the data volume."
    echo "[chat2db]  Set CHAT2DB_COMMUNITY_PASSWORD to use your own instead."
    echo "[chat2db] ============================================================"
  else
    rm -f "${tmp_file}"
  fi
}

if [ "${CHAT2DB_DISABLE_LOGIN:-false}" = "true" ]; then
  unset CHAT2DB_COMMUNITY_PASSWORD
  echo "[chat2db] Sign-in disabled by CHAT2DB_DISABLE_LOGIN. Anyone who can reach"
  echo "[chat2db] this port has full access to every stored connection."
elif [ -n "${CHAT2DB_COMMUNITY_PASSWORD:-}" ]; then
  echo "[chat2db] Using the sign-in password from CHAT2DB_COMMUNITY_PASSWORD"
else
  if [ ! -f "${PASSWORD_FILE}" ]; then
    generate_password_file "${PASSWORD_FILE}"
  else
    echo "[chat2db] Using the sign-in password from ${PASSWORD_FILE}"
  fi
  if [ -f "${PASSWORD_FILE}" ]; then
    CHAT2DB_COMMUNITY_PASSWORD="$(cat "${PASSWORD_FILE}")"
    export CHAT2DB_COMMUNITY_PASSWORD
  fi
fi

# Bean initialisation strategy.
#
# Lazy is the application default and stays the default here: it starts fast and
# builds each subsystem the first time something touches it. Eager initialisation
# moves that work into startup, where the healthcheck already gates readiness, so
# the first page load does not pay for it - but it also builds beans that lazy
# initialisation never reaches, and one of those failing takes the whole
# container down rather than one request. Opt in with CHAT2DB_EAGER_INIT=true and
# watch the first start.
lazy_init=true
if [ "${CHAT2DB_EAGER_INIT:-false}" = "true" ]; then
  lazy_init=false
fi

# Word splitting on JAVA_OPTS is intended: it carries multiple JVM flags.
# shellcheck disable=SC2086
exec java \
  -Dloader.path=/app/lib \
  -Dspring.main.lazy-initialization="${lazy_init}" \
  -Dchat2db.gui=false \
  -Dchat2db.runtime.mode=community \
  -Dchat2db.network.status=OFFLINE \
  -Dserver.address="${SERVER_ADDRESS}" \
  -Dserver.port="${SERVER_PORT}" \
  -Dspring.profiles.active=release \
  ${JAVA_OPTS:-} \
  -jar /app/chat2db-community.jar \
  "$@"

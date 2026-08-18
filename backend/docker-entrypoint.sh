#!/bin/sh
set -eu

storage_root="${FILE_STORAGE_ROOT:-/app/uploads}"

if [ "$(id -u)" -eq 0 ]; then
    mkdir -p "$storage_root"
    chown -R spring:spring "$storage_root"
    exec su-exec spring:spring "$@"
fi

exec "$@"

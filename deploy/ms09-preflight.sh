#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
sha256sum -c SHA256SUMS
sysctl net.ipv4.ip_forward
free -h
df -h / /var/lib/docker
docker compose version
docker ps --format 'table {{.Names}}\t{{.Status}}'
echo 'Read-only preflight complete. No images loaded, services stopped, or databases modified.'

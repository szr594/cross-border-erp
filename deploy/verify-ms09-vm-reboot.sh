#!/usr/bin/env bash
# Read-only deployment evidence. Run before and after an operator-controlled reboot.
set -euo pipefail
umask 077
cd /opt/cross-border-erp
mode="${1:?usage: bash verify-ms09-vm-reboot.sh before|after}"
state="/opt/cross-border-erp/backups/ms09-reboot"
case "$mode" in before|after) ;; *) exit 2;; esac
mkdir -p "$state"
if [ "$mode" = before ]; then
  docker ps --format '{{.Names}}' | grep -Ei 'procurement|purchase-service' >/dev/null || {
    echo 'MS-09采购服务未部署：不能执行本阶段重启验收。' >&2; exit 1;
  }
  cat /proc/sys/kernel/random/boot_id > "$state/boot.before"
  docker ps --format '{{.Names}}' | grep '^cross-border-erp-production-' | sort > "$state/containers.before"
  while IFS= read -r name; do
    docker inspect --format '{{.Name}} {{.Image}} {{.HostConfig.RestartPolicy.Name}}' "$name"
  done < "$state/containers.before" > "$state/images.before"
  if grep -E ' (no|on-failure)$' "$state/images.before"; then
    echo '请先完成容器开机自启配置，再重启验收。' >&2; exit 1
  fi
  echo '已保存重启前基线。还需保存待发布事件ID及对应业务断言，再由操作者执行 reboot。'
else
  test -s "$state/boot.before"
  test "$(cat /proc/sys/kernel/random/boot_id)" != "$(cat "$state/boot.before")" || {
    echo 'boot_id未变化，不能视为整机重启。' >&2; exit 1;
  }
  test "$(sysctl -n net.ipv4.ip_forward)" = 1
  systemctl is-active --quiet docker
  while IFS= read -r name; do
    result="$(docker inspect --format '{{.State.Running}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name")"
    case "$result" in 'true healthy'|'true none') ;; *) echo "$name $result" >&2; exit 1;; esac
  done < "$state/containers.before"
  while IFS= read -r name; do
    docker inspect --format '{{.Name}} {{.Image}} {{.HostConfig.RestartPolicy.Name}}' "$name"
  done < "$state/containers.before" > "$state/images.after"
  diff -u "$state/images.before" "$state/images.after"
  curl --noproxy '*' --fail --silent --show-error --max-time 10 http://127.0.0.1/ >/dev/null
  echo '整机重启与容器恢复检查通过；消息补偿、Inbox数量和登录业务断言需另行核对，不能仅凭本脚本认定整体验收通过。'
fi

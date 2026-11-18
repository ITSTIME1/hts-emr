#!/usr/bin/env bash

set -euo pipefail

DEPLOY_DIR=${DEPLOY_DIR:-$HOME/hts-deploy}
cd "$DEPLOY_DIR"

ACTIVE_CONF=nginx/upstream/active.conf
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-180}
NGINX_URL=${NGINX_URL:-http://127.0.0.1:8080}
IMAGE=${IMAGE:-ghcr.io/itstime1/hts-emr}   # compose.yml의 image 주소와 같아야 한다

log()  { printf '\033[1;34m[deploy]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[deploy] 중단:\033[0m %s\n' "$*" >&2; exit 1; }
    


active_color() {
  [ -f "$ACTIVE_CONF" ] || fail "$DEPLOY_DIR/$ACTIVE_CONF 가 없습니다"
  grep -o 'app-[a-z]*' "$ACTIVE_CONF" | head -1 | sed 's/app-//'
}
other_color() { [ "$1" = blue ] && echo green || echo blue; }
port_of()     { [ "$1" = blue ] && echo 8081 || echo 8082; }

tag_of() {
  local key; key="$(echo "$1" | tr a-z A-Z)_TAG"
  grep "^$key=" .env 2>/dev/null | cut -d= -f2 || true
}

set_tag() {  
  local key; key="$(echo "$1" | tr a-z A-Z)_TAG"
  touch .env
  { grep -v "^$key=" .env || true; echo "$key=$2"; } > .env.tmp && mv .env.tmp .env
}

LOCK_DIR=.deploy.lock

lock() {
  mkdir "$LOCK_DIR" 2>/dev/null || fail "다른 배포가 진행 중입니다 ($DEPLOY_DIR/$LOCK_DIR). 진행 중인 게 없다면 이 폴더를 지우세요"
  trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT
}

container_of() { docker compose ps -q "app-$1" 2>/dev/null || true; }

health_of() {
  local id; id=$(container_of "$1")
  [ -z "$id" ] && { echo "없음"; return; }
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id"
}

restarts_of() {
  local id; id=$(container_of "$1")
  [ -z "$id" ] && { echo 0; return; }
  docker inspect -f '{{.RestartCount}}' "$id"
}


wait_healthy() {
  local color=$1 waited=0 s
  log "app-$color 가 healthy가 될 때까지 기다림 (최대 ${HEALTH_TIMEOUT}초)"
  while :; do
    s=$(health_of "$color")
    # 부팅 중 죽으면 restart 정책 때문에 계속 재시작하며 starting으로 보인다. 재시작 횟수로 빨리 알아챈다
    [ "$(restarts_of "$color")" -gt 0 ] && s=crashed
    case "$s" in
      healthy)
        log "app-$color healthy (${waited}초)"; return 0 ;;
      unhealthy|exited|dead|crashed|없음)
        docker compose logs --tail 40 "app-$color" >&2 || true
        # 그냥 두면 restart 정책 때문에 실패한 부팅을 계속 되풀이한다. 멈춰 둔다
        docker compose stop "app-$color" >/dev/null 2>&1 || true
        fail "app-$color 상태가 $s 입니다. 트래픽은 그대로 app-$(active_color) 에 있습니다" ;;
    esac
    [ "$waited" -ge "$HEALTH_TIMEOUT" ] && fail "app-$color 가 ${HEALTH_TIMEOUT}초 안에 healthy가 되지 않았습니다"
    sleep 3; waited=$((waited + 3))
  done
}
active_header() {
  curl -s -o /dev/null -D - "$NGINX_URL/actuator/health" 2>/dev/null \
    | tr -d '\r' | awk -F': ' 'tolower($1)=="x-active-color"{print $2}' || true
}

nginx_sees() {
  docker compose exec -T nginx grep -q "server app-$1:8080;" /etc/nginx/upstream/active.conf 2>/dev/null
}

reload_when_visible() {
  local color=$1 i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    nginx_sees "$color" && break
    [ "$i" = 10 ] && { echo "nginx 컨테이너가 10초 동안 새 active.conf($color)를 보지 못했습니다" >&2; return 1; }
    sleep 1
  done
  docker compose exec -T nginx nginx -t >/dev/null 2>&1 || { docker compose exec -T nginx nginx -t >&2 || true; return 1; }
  docker compose exec -T nginx nginx -s reload >/dev/null 2>&1
}

write_active_conf() {
  cat > "$ACTIVE_CONF.tmp" <<CONF
# 지금 서비스 중인 쪽. deploy.sh가 관리한다 ($(date '+%Y-%m-%d %H:%M:%S'))
upstream emr_app {
    server app-$1:8080;
    keepalive 32;
}
map \$host \$active_color { default "$1"; }
CONF
  mv "$ACTIVE_CONF.tmp" "$ACTIVE_CONF"
}

rollback_conf() {
  local prev=$1
  write_active_conf "$prev"
  reload_when_visible "$prev" || true
}

point_nginx_to() {
  local color=$1 prev; prev=$(active_color)
  write_active_conf "$color"

  if ! reload_when_visible "$color"; then
    rollback_conf "$prev"
    fail "nginx에 새 설정을 적용하지 못했습니다. active.conf를 app-$prev 로 되돌렸습니다"
  fi

  # reload는 비동기라 잠깐 옛 worker가 응답할 수 있다. 몇 번 다시 확인한다
  local i got=""
  for i in 1 2 3 4 5 6 7 8 9 10; do
    got=$(active_header)
    [ "$got" = "$color" ] && { log "nginx → app-$color 전환 완료"; return 0; }
    sleep 1
  done
  rollback_conf "$prev"
  fail "reload 후에도 응답 헤더가 $color 가 아닙니다 (받은 값: ${got:-없음}). active.conf를 app-$prev 로 되돌렸습니다"
}




# ---------- 명령 ----------
cmd_status() {
  local a c; a=$(active_color)
  for c in blue green; do
    printf '  %-6s %-18s %-10s %s\n' "$c" "hts-emr:$(tag_of "$c")" "$(health_of "$c")" \
      "$([ "$c" = "$a" ] && echo '◀ 트래픽 받는 중' || true)"
  done
  echo "  nginx 응답 헤더 X-Active-Color: $(active_header)"
}

cmd_deploy() {
  local tag=$1
  # 로컬에서 다시 빌드하지 않고, CI가 GHCR에 올려 둔 이미지를 그대로 받는다. 받지 못하면 아무것도 바꾸기 전에 중단
  docker pull "$IMAGE:$tag" >/dev/null 2>&1 \
    || fail "이미지 $IMAGE:$tag 를 받지 못했습니다 (태그가 맞는지, GHCR 로그인이 되어 있는지 확인하세요)"
  lock

  local from to; from=$(active_color); to=$(other_color "$from")
  log "지금 app-$from (hts-emr:$(tag_of "$from")) 이 서비스 중. 대기 쪽 app-$to 에 hts-emr:$tag 를 올립니다"

  # 1) 대기 쪽만 새 이미지로 다시 만든다. --no-deps: oracle/nginx는 건드리지 않는다
  set_tag "$to" "$tag"
  docker compose up -d --no-deps --force-recreate "app-$to"

  # 2) 관문: healthy + 재시작 없음
  wait_healthy "$to"

  # 3) 관문: nginx를 거치지 않고 새 쪽에 직접 물어본다
  log "nginx를 거치지 않고 app-$to 를 직접 확인 (127.0.0.1:$(port_of "$to"))"
  curl -fs "http://127.0.0.1:$(port_of "$to")/actuator/health/readiness" >/dev/null \
    || fail "app-$to 직접 확인 실패. 트래픽은 그대로 app-$from 에 있습니다"

  # 4) 전환
  point_nginx_to "$to"
  log "완료: hts-emr:$tag 가 app-$to 에서 서비스 중"
  log "app-$from (hts-emr:$(tag_of "$from")) 은 롤백용으로 켜 둡니다. 되돌리려면: deploy.sh rollback"
}

cmd_rollback() {
  lock
  local from to; from=$(active_color); to=$(other_color "$from")
  [ "$(health_of "$to")" = healthy ] || fail "되돌아갈 app-$to 가 healthy가 아닙니다($(health_of "$to"))"
  log "롤백: app-$from → app-$to (hts-emr:$(tag_of "$to"))"
  log "주의: 롤백은 트래픽만 되돌립니다. 새 버전이 바꾼 DB 스키마는 그대로입니다"
  point_nginx_to "$to"
}

cmd_stop_idle() {
  local idle; idle=$(other_color "$(active_color)")
  log "대기 쪽 app-$idle 을 끕니다. 이후에는 롤백할 곳이 없습니다"
  docker compose stop "app-$idle"
}

case "${1:-}" in
  ""|-h|--help) sed -n '2,6p' "$0"; exit 1 ;;
  status)    cmd_status ;;
  rollback)  cmd_rollback ;;
  stop-idle) cmd_stop_idle ;;
  *)         cmd_deploy "$1" ;;
esac



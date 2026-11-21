#!/usr/bin/env bash
# 重新打包并重启服务（一条命令走完，避免"边跑服务边打包"把进程搞死）。
#
# 为什么必须按这个顺序：
#   Spring Boot 的 repackage 是**原地重写** target 里的 jar（先写 .original，再拼成 fat jar）。
#   服务运行中时，LaunchedURLClassLoader 是懒加载的，jar 一旦被替换/清空，
#   后续要用的类就读不到了，表现为：
#       NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy
#   Tomcat 线程池连环抛错，进程直接退出，且日志里没有 "Stopping service" 这类正常收尾。
#   所以：先停服务，再打包，校验清单，最后启动。
#
# 用法：
#   ./tools/rebuild.sh            # 停服务 + 打包 + 启动
#   ./tools/rebuild.sh -Dgpu=true # 额外参数透传给 mvn（如 GPU 包）
#   PORT=6889 ./tools/rebuild.sh  # 换端口
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

PORT="${PORT:-6888}"
JAR="target/java-vision-plus-0.0.1-SNAPSHOT.jar"
# 运行用副本：mvn clean 会删 target，别人（或 IDE、并行会话）打包时，
# 从 target 里正在运行的 jar 会被删除/重写，进程必挂。副本放在 .run/，clean 碰不到。
RUNTIME_DIR=".run"
RUNTIME_JAR="${RUNTIME_DIR}/app.jar"
LOG="${LOG:-/tmp/vision-run.log}"
MVN_ARGS=("$@")

info() { printf '\033[36m[rebuild]\033[0m %s\n' "$*"; }
fail() { printf '\033[31m[rebuild]\033[0m %s\n' "$*"; exit 1; }

# ---------- 1. 停服务 ----------
stop_service() {
  local pid
  pid=$(netstat -ano 2>/dev/null | grep -E ":${PORT}\s+.*LISTENING" | head -1 | awk '{print $5}')
  if [ -z "${pid}" ]; then
    info "端口 ${PORT} 空闲，无需停止"
    return 0
  fi
  info "停止占用 ${PORT} 的进程 PID=${pid}"
  taskkill //PID "${pid}" //F >/dev/null 2>&1
  for _ in $(seq 1 20); do
    netstat -ano 2>/dev/null | grep -qE ":${PORT}\s+.*LISTENING" || return 0
    sleep 1
  done
  fail "PID=${pid} 未能在 20s 内释放端口 ${PORT}"
}

# ---------- 2. 打包 ----------
build() {
  info "打包中（服务已停，jar 可安全重写）..."
  if ! mvn -o package -DskipTests "${MVN_ARGS[@]}" > /tmp/vision-build.log 2>&1; then
    tail -30 /tmp/vision-build.log
    fail "mvn package 失败，详见 /tmp/vision-build.log"
  fi
}

# ---------- 3. 校验清单 ----------
verify_jar() {
  [ -f "${JAR}" ] || fail "未找到 ${JAR}"
  local size main
  size=$(stat -c %s "${JAR}")
  main=$(unzip -p "${JAR}" META-INF/MANIFEST.MF 2>/dev/null | grep -c 'Main-Class: org.springframework.boot.loader.JarLauncher')
  if [ "${main}" -lt 1 ]; then
    fail "${JAR} 缺少 Main-Class（repackage 未完成，大小 $((size / 1024 / 1024))MB）"
  fi
  info "jar 校验通过：$((size / 1024 / 1024))MB，Main-Class 正常"
}

# ---------- 4. 复制运行期副本 ----------
prepare_runtime_jar() {
  mkdir -p "${RUNTIME_DIR}"
  info "复制运行副本到 ${RUNTIME_JAR}（此后 target 被 clean 也不影响服务）"
  cp "${JAR}" "${RUNTIME_JAR}" || fail "复制 ${JAR} 失败"
  local main
  main=$(unzip -p "${RUNTIME_JAR}" META-INF/MANIFEST.MF 2>/dev/null | grep -c 'Main-Class: org.springframework.boot.loader.JarLauncher')
  [ "${main}" -ge 1 ] || fail "副本清单异常"
}

# ---------- 5. 启动 ----------
start_service() {
  info "启动服务，日志 ${LOG}"
  # env -u SERVER__PORT：宿主机若注入该变量，Spring 宽松绑定会把它当成 server.port
  env -u SERVER__PORT -u SERVER__HOST \
    java -Xms1g -Xmx2g -jar "${RUNTIME_JAR}" > "${LOG}" 2>&1 &
  local pid=$!
  for i in $(seq 1 60); do
    if curl -s -m 2 "http://127.0.0.1:${PORT}/system/status" >/dev/null 2>&1; then
      info "端口就绪 ${i}s（PID=${pid}）"
      wait_warmup
      return 0
    fi
    sleep 1
  done
  tail -20 "${LOG}"
  fail "启动超时，详见 ${LOG}"
}

wait_warmup() {
  local state
  for _ in $(seq 1 60); do
    state=$(curl -s "http://127.0.0.1:${PORT}/system/status" \
      | python -c "import sys,json;w=json.load(sys.stdin)['data']['inference']['warmup'];print(w['state'],w['loaded'],w['failed'],w['costMs'])" 2>/dev/null)
    case "${state}" in
      预热中*|"") sleep 2 ;;
      *) info "预热: ${state}"; return 0 ;;
    esac
  done
  info "预热未在预期时间内完成，可继续用（失败项会在首次请求重试）"
}

stop_service
build
verify_jar
prepare_runtime_jar
start_service
info "完成。"

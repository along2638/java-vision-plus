#!/usr/bin/env bash
# 并发压测：对指定接口按不同并发度发请求，统计每个请求的耗时分布。
# 用法: ./bench.sh <endpoint> <image> <totalRequests> <concurrency...>
# 说明：不用 xargs（Git Bash 的 xargs 在部分环境会断言失败），改用后台任务 + 限流。
set -u

ENDPOINT=${1:-/word/json}
IMAGE=${2:-image/ocr-bench.png}
TOTAL=${3:-12}
shift 3 || true
CONCURRENCY=${*:-"1 2 4 6"}

OUT_DIR="target/bench"
mkdir -p "$OUT_DIR"

echo "接口: $ENDPOINT  图片: $IMAGE  每轮请求数: $TOTAL"
printf '%-6s %-9s %-9s %-9s %-9s %-10s\n' "并发" "最小(s)" "平均(s)" "P95(s)" "最大(s)" "吞吐(/s)"

for C in $CONCURRENCY; do
  rm -f "$OUT_DIR"/c-"$C"-*.txt
  START=$(date +%s.%N)
  for i in $(seq "$TOTAL"); do
    # 控制同时在跑的任务数
    while [ "$(jobs -rp | wc -l)" -ge "$C" ]; do sleep 0.02; done
    ( curl -s -o /dev/null -w "%{time_total} %{http_code}\n" \
        -F "file=@$IMAGE" "http://127.0.0.1:6888$ENDPOINT" > "$OUT_DIR/c-$C-$i.txt" 2>/dev/null ) &
  done
  wait
  END=$(date +%s.%N)
  cat "$OUT_DIR"/c-"$C"-*.txt > "$OUT_DIR/conc-$C.txt"
  python - "$OUT_DIR/conc-$C.txt" "$C" "$START" "$END" <<'PY'
import sys, statistics
path, conc, start, end = sys.argv[1], sys.argv[2], float(sys.argv[3]), float(sys.argv[4])
rows = [l.split() for l in open(path).read().strip().splitlines() if l.strip()]
if not rows:
    print("%-6s 无有效响应" % conc)
    sys.exit(0)
times = sorted(float(r[0]) for r in rows)
codes = {r[1] for r in rows}
wall = end - start
p95 = times[max(0, int(len(times) * 0.95) - 1)]
bad = "" if codes == {"200"} else "  !! HTTP " + ",".join(sorted(codes))
print("%-6s %-9.2f %-9.2f %-9.2f %-9.2f %-10.2f%s" % (
    conc, times[0], statistics.mean(times), p95, times[-1], len(times) / wall, bad))
PY
done

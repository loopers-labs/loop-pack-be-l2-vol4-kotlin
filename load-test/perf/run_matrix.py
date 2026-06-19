#!/usr/bin/env python3
"""Week5 L5 부하측정 오케스트레이터 (Mac 실행).
런마다: 앱 재시작(cold/warm) → k6 부하 → k6 summary + cgroup 델타 + Prometheus(host/JVM) 수집 → runs/<id>.{json,md} 저장.
사용:
  python3 run_matrix.py smoke                 # 30s 스모크 1런 (검증용)
  python3 run_matrix.py one baseline 1000 warm
  python3 run_matrix.py matrix                # 전체 12런 (baseline 6 → indexing 6)
"""
import subprocess, json, time, sys, os, urllib.request, urllib.parse

N100 = "175.208.203.10"
BASE = f"http://{N100}:8080"
PROM = "http://localhost:9090"
SSH = ["ssh", "-o", "ConnectTimeout=6", f"won@{N100}"]
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # repo root
RUNS_DIR = os.path.join(ROOT, "load-test/results/week5-l5/runs")
K6 = os.path.join(ROOT, "load-test/k6/product-list-load.js")
CSV = os.path.join(ROOT, "load-test/results/week5-l5/matrix-results.csv")
MEASURE_DURATION = "3m"
MEASURE_SECONDS = 180

def sh(cmd):
    return subprocess.run(cmd, capture_output=True, text=True)

def ssh(remote):
    return sh(SSH + [remote])

def log(m):
    print(f"[{time.strftime('%H:%M:%S')}] {m}", flush=True)

def restart_app():
    log("앱 재시작…")
    ssh("docker restart perf-app")
    for _ in range(45):
        h = ssh('curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health').stdout.strip()
        if h == "200":
            log("health 200")
            return True
        time.sleep(2)
    raise RuntimeError("앱 health 200 실패")

def cgroup_sample():
    out = ssh("bash ~/perf/sample-cgroup.sh").stdout.strip().splitlines()
    d = {}
    for line in out:
        p = line.split()
        if len(p) >= 5:
            d[p[0]] = {"usage_usec": int(p[1]), "throttled_usec": int(p[2]), "nr_throttled": int(p[3]), "mem": int(p[4])}
    return d

def set_index(state):
    log(f"인덱스 상태 = {state}")
    for drop in ("DROP INDEX idx_p_lc_id ON product;", "DROP INDEX idx_p_brand_lc_id ON product;"):
        ssh(f'docker exec perf-mysql mysql -uapplication -papplication loopers -e "{drop}" 2>/dev/null')
    if state == "indexing":
        ssh('docker exec perf-mysql mysql -uapplication -papplication loopers -e '
            '"CREATE INDEX idx_p_lc_id ON product(like_count, id); '
            'CREATE INDEX idx_p_brand_lc_id ON product(brand_id, like_count, id);"')
    idx = ssh('docker exec perf-mysql mysql -uapplication -papplication loopers -N -e '
              '"SELECT GROUP_CONCAT(DISTINCT INDEX_NAME) FROM information_schema.statistics '
              'WHERE table_schema=\\"loopers\\" AND table_name=\\"product\\";"').stdout.strip()
    log(f"product 인덱스: {idx}")
    return idx

def prom_range(query, start, end, step=5):
    url = PROM + "/api/v1/query_range?" + urllib.parse.urlencode(
        {"query": query, "start": start, "end": end, "step": step})
    try:
        d = json.load(urllib.request.urlopen(url, timeout=15))
        res = d["data"]["result"]
        if not res:
            return []
        return [float(v[1]) for v in res[0]["values"] if v[1] not in ("NaN", "+Inf", "-Inf")]
    except Exception as e:
        log(f"prom_range 실패 {query[:40]}: {e}")
        return []

def stat(vals):
    if not vals:
        return {"avg": None, "max": None, "first": None, "last": None, "delta": None}
    return {"avg": round(sum(vals)/len(vals), 4), "max": round(max(vals), 4),
            "first": round(vals[0], 4), "last": round(vals[-1], 4), "delta": round(vals[-1]-vals[0], 4)}

def k6_run(mode, rate, duration, out_json=None, out_txt=None):
    env = dict(os.environ, BASE=BASE, MODE=mode)
    cmd = ["k6", "run", "-e", f"BASE={BASE}", "-e", f"MODE={mode}"]
    if mode == "warmup":
        cmd += ["-e", f"PEAK={rate}"]
    else:
        cmd += ["-e", f"RATE={rate}", "-e", f"DURATION={duration}"]
    if out_json:
        cmd += ["--summary-export", out_json]
    cmd += [K6]
    log("k6 " + " ".join(cmd[3:]))
    r = subprocess.run(cmd, capture_output=True, text=True)
    if out_txt:
        with open(out_txt, "w") as f:
            f.write(r.stdout + "\n--- STDERR ---\n" + r.stderr)
    return r

def parse_k6(path):
    try:
        d = json.load(open(path))
        m = d["metrics"]
        def v(name, key):
            # k6 v1.x summary-export: 통계가 metric 객체 최상위에 위치
            return m.get(name, {}).get(key)
        return {
            "reqs": v("http_reqs", "count"), "achieved_rps": v("http_reqs", "rate"),
            "fail_rate": v("http_req_failed", "value"),
            "dropped": m.get("dropped_iterations", {}).get("count"),
            "dur_avg": v("http_req_duration", "avg"), "dur_med": v("http_req_duration", "med"),
            "dur_p90": v("http_req_duration", "p(90)"), "dur_p95": v("http_req_duration", "p(95)"),
            "dur_p99": v("http_req_duration", "p(99)"), "dur_max": v("http_req_duration", "max"),
        }
    except Exception as e:
        log(f"parse_k6 실패: {e}")
        return {}

def run_one(state, tps, warmup, smoke=False):
    rid = f"{state}-{tps}-{warmup}" + ("-smoke" if smoke else "")
    log(f"===== RUN {rid} =====")
    os.makedirs(RUNS_DIR, exist_ok=True)
    restart_app()
    if warmup == "warm":
        log("웜업 1분(JIT 유도)…")
        k6_run("warmup", tps, None)
        time.sleep(2)
    dur = "30s" if smoke else MEASURE_DURATION
    dur_s = 30 if smoke else MEASURE_SECONDS
    before = cgroup_sample()
    start = time.time()
    out_json = os.path.join(RUNS_DIR, rid + ".k6.json")
    out_txt = os.path.join(RUNS_DIR, rid + ".k6.txt")
    k6_run("measure", tps, dur, out_json, out_txt)
    end = time.time()
    after = cgroup_sample()
    actual = end - start

    # cgroup 컨테이너별 CPU-초 델타 → 평균 코어
    cg = {}
    for name in before:
        du = (after.get(name, {}).get("usage_usec", 0) - before[name]["usage_usec"]) / 1e6
        cg[name] = {"cpu_sec": round(du, 2), "avg_cores": round(du/actual, 3),
                    "mem_mb": round(after.get(name, {}).get("mem", 0)/1048576, 1),
                    "nr_throttled": after.get(name, {}).get("nr_throttled", 0) - before[name]["nr_throttled"]}

    s, e = int(start), int(end)
    host_busy = stat(prom_range('clamp(1 - avg(rate(node_cpu_seconds_total{mode="idle"}[1m])), 0, 1)', s, e))
    host_iowait = stat(prom_range('clamp(avg(rate(node_cpu_seconds_total{mode="iowait"}[1m])), 0, 1)', s, e))
    psi_cpu = stat(prom_range('node_pressure_cpu_waiting_seconds_total', s, e))
    psi_io = stat(prom_range('node_pressure_io_waiting_seconds_total', s, e))
    proc_cpu = stat(prom_range('process_cpu_usage{job="commerce-api"}', s, e))
    jit = stat(prom_range('jvm_compilation_time_ms_total{job="commerce-api"}', s, e))
    gc = stat(prom_range('jvm_gc_pause_seconds_sum{job="commerce-api"}', s, e))
    hikari_pending = stat(prom_range('hikaricp_connections_pending{job="commerce-api"}', s, e))
    hikari_active = stat(prom_range('hikaricp_connections_active{job="commerce-api"}', s, e))

    k6r = parse_k6(out_json)
    result = {"run_id": rid, "state": state, "tps": tps, "warmup": warmup,
              "duration_s": round(actual, 1), "start_epoch": s, "end_epoch": e,
              "k6": k6r, "cgroup": cg,
              "host": {"cpu_busy": host_busy, "iowait": host_iowait,
                       "psi_cpu_waiting_delta_s": psi_cpu["delta"], "psi_io_waiting_delta_s": psi_io["delta"]},
              "jvm": {"process_cpu": proc_cpu, "jit_compile_ms_delta": jit["delta"],
                      "gc_pause_s_delta": gc["delta"], "hikari_pending_max": hikari_pending["max"],
                      "hikari_active_max": hikari_active["max"]}}
    json.dump(result, open(os.path.join(RUNS_DIR, rid + ".json"), "w"), indent=2, ensure_ascii=False)
    write_md(result)
    append_csv(result)
    log(f"완료 {rid}: achieved={k6r.get('achieved_rps')} p95={k6r.get('dur_p95')}ms fail={k6r.get('fail_rate')} "
        f"hostCPU={host_busy['avg']} appCPU_cores={cg.get('perf-app',{}).get('avg_cores')}")
    return result

def write_md(r):
    k = r["k6"]; cg = r["cgroup"]; h = r["host"]; j = r["jvm"]
    def f(x, suf=""): return ("—" if x is None else f"{x}{suf}")
    md = f"""# Run: {r['run_id']}

| 조건 | 값 |
|---|---|
| 인덱스 | {r['state']} |
| 목표 TPS | {r['tps']} |
| 웜업 | {r['warmup']} |
| 측정시간 | {r['duration_s']}s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | {f(k.get('achieved_rps'))} (목표 {r['tps']}) |
| 총 요청 | {f(k.get('reqs'))} |
| 실패율 | {f(k.get('fail_rate'))} |
| 응답 med | {f(k.get('dur_med'),'ms')} |
| 응답 p90 | {f(k.get('dur_p90'),'ms')} |
| 응답 p95 | {f(k.get('dur_p95'),'ms')} |
| 응답 p99 | {f(k.get('dur_p99'),'ms')} |
| 응답 max | {f(k.get('dur_max'),'ms')} |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
""" + "".join(f"| {n} | {cg[n]['cpu_sec']} | {cg[n]['avg_cores']} | {cg[n]['mem_mb']} |\n" for n in cg) + f"""
## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | {f(h['cpu_busy']['avg'])} | {f(h['cpu_busy']['max'])} |
| iowait | {f(h['iowait']['avg'])} | {f(h['iowait']['max'])} |
| PSI cpu waiting Δ(s) | {f(h['psi_cpu_waiting_delta_s'])} | |
| PSI io waiting Δ(s) | {f(h['psi_io_waiting_delta_s'])} | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | {f(j['process_cpu']['avg'])} / {f(j['process_cpu']['max'])} |
| JIT 컴파일 Δ(ms) | {f(j['jit_compile_ms_delta'])} |
| GC pause Δ(s) | {f(j['gc_pause_s_delta'])} |
| Hikari pending max | {f(j['hikari_pending_max'])} |
| Hikari active max | {f(j['hikari_active_max'])} |
"""
    open(os.path.join(RUNS_DIR, r["run_id"] + ".md"), "w").write(md)

def append_csv(r):
    k = r["k6"]; cg = r["cgroup"]; h = r["host"]; j = r["jvm"]
    head = ("run_id,state,tps,warmup,achieved_rps,fail_rate,p50,p95,p99,max,"
            "host_cpu_avg,host_cpu_max,psi_cpu_d,app_cores,mysql_cores,redis_cores,"
            "jit_ms_d,gc_s_d,hikari_pending_max\n")
    if not os.path.exists(CSV):
        open(CSV, "w").write(head)
    def g(x): return "" if x is None else x
    redis = round(cg.get('perf-redis-master', {}).get('avg_cores', 0) + cg.get('perf-redis-readonly', {}).get('avg_cores', 0), 3)
    row = [r["run_id"], r["state"], r["tps"], r["warmup"], g(k.get("achieved_rps")), g(k.get("fail_rate")),
           g(k.get("dur_med")), g(k.get("dur_p95")), g(k.get("dur_p99")), g(k.get("dur_max")),
           g(h["cpu_busy"]["avg"]), g(h["cpu_busy"]["max"]), g(h["psi_cpu_waiting_delta_s"]),
           g(cg.get("perf-app", {}).get("avg_cores")), g(cg.get("perf-mysql", {}).get("avg_cores")), redis,
           g(j["jit_compile_ms_delta"]), g(j["gc_pause_s_delta"]), g(j["hikari_pending_max"])]
    open(CSV, "a").write(",".join(str(x) for x in row) + "\n")

def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "smoke"
    if cmd == "smoke":
        set_index("baseline")
        run_one("baseline", 100, "cold", smoke=True)
    elif cmd == "one":
        state, tps, warmup = sys.argv[2], int(sys.argv[3]), sys.argv[4]
        set_index(state)
        run_one(state, tps, warmup)
    elif cmd == "matrix":
        for state in ("baseline", "indexing"):
            set_index(state)
            for tps in (100, 1000, 3000):
                for warmup in ("cold", "warm"):
                    run_one(state, tps, warmup)
        log("매트릭스 완료")

if __name__ == "__main__":
    main()

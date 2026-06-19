#!/usr/bin/env python3
"""첫 페이지 캐시 off/on 비교 (indexing·warm·글로벌 첫페이지 100%).
캐시 토글 = 앱 컨테이너를 SPRING_CACHE_TYPE 으로 재생성. run_matrix 의 수집 헬퍼 재사용.
사용: python3 run_cache_experiment.py
"""
import time, os, json, subprocess
import run_matrix as rm

RUNS = rm.RUNS_DIR
CSV = os.path.join(rm.ROOT, "load-test/results/week5-l5/cache-results.csv")
TPS_LIST = [1000, 3000]

CACHE_SPRING_VALUE = {"off": "none", "on": "redis"}  # spring.cache.type 은 none/redis 만 유효

def recreate_app(cache):
    spring_value = CACHE_SPRING_VALUE[cache]
    rm.log(f"앱 재생성 (cache={cache} → spring.cache.type={spring_value})…")
    rm.ssh(f'cd ~/perf && SPRING_CACHE_TYPE={spring_value} docker compose -f perf-stack-compose.yml up -d --force-recreate commerce-api')
    for _ in range(45):
        if rm.ssh('curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health').stdout.strip() == "200":
            rm.log("health 200"); return
        time.sleep(2)
    raise RuntimeError("health 실패")

def redis_stats():
    out = rm.ssh('docker exec perf-redis-master redis-cli INFO stats').stdout
    d = {}
    for line in out.splitlines():
        if line.startswith("keyspace_hits:"): d["hits"] = int(line.split(":")[1])
        if line.startswith("keyspace_misses:"): d["misses"] = int(line.split(":")[1])
    return d

def k6_global(mode, rate, duration, out_json=None, out_txt=None):
    cmd = ["k6", "run", "-e", f"BASE={rm.BASE}", "-e", f"MODE={mode}", "-e", "BRAND_RATIO=0"]
    cmd += (["-e", f"PEAK={rate}"] if mode == "warmup" else ["-e", f"RATE={rate}", "-e", f"DURATION={duration}"])
    if out_json: cmd += ["--summary-export", out_json]
    cmd += [rm.K6]
    rm.log("k6 " + " ".join(cmd[3:]))
    r = subprocess.run(cmd, capture_output=True, text=True)
    if out_txt: open(out_txt, "w").write(r.stdout + "\n--STDERR--\n" + r.stderr)
    return r

def run(cache, tps):
    rid = f"cache-{cache}-{tps}-warm"
    rm.log(f"===== {rid} =====")
    before = rm.cgroup_sample(); rstat0 = redis_stats()
    start = time.time()
    oj = os.path.join(RUNS, rid + ".k6.json"); ot = os.path.join(RUNS, rid + ".k6.txt")
    k6_global("measure", tps, "3m", oj, ot)
    end = time.time(); after = rm.cgroup_sample(); rstat1 = redis_stats(); actual = end - start
    cg = {n: {"cpu_sec": round((after.get(n, {}).get("usage_usec", 0) - before[n]["usage_usec"]) / 1e6, 2),
              "avg_cores": round((after.get(n, {}).get("usage_usec", 0) - before[n]["usage_usec"]) / 1e6 / actual, 3),
              "mem_mb": round(after.get(n, {}).get("mem", 0) / 1048576, 1)} for n in before}
    s, e = int(start), int(end)
    host = rm.stat(rm.prom_range('clamp(1 - avg(rate(node_cpu_seconds_total{mode="idle"}[1m])), 0, 1)', s, e))
    proc = rm.stat(rm.prom_range('process_cpu_usage{job="commerce-api"}', s, e))
    k6r = rm.parse_k6(oj)
    hits = rstat1.get("hits", 0) - rstat0.get("hits", 0); misses = rstat1.get("misses", 0) - rstat0.get("misses", 0)
    hit_ratio = round(hits / (hits + misses), 4) if (hits + misses) else None
    res = {"run_id": rid, "cache": cache, "tps": tps, "duration_s": round(actual, 1), "k6": k6r,
           "cgroup": cg, "host_cpu": host, "process_cpu": proc,
           "redis": {"hits": hits, "misses": misses, "hit_ratio": hit_ratio}}
    json.dump(res, open(os.path.join(RUNS, rid + ".json"), "w"), indent=2, ensure_ascii=False)
    append_csv(res)
    rm.log(f"완료 {rid}: achieved={k6r.get('achieved_rps')} p95={k6r.get('dur_p95')} fail={k6r.get('fail_rate')} "
           f"app_cores={cg.get('perf-app',{}).get('avg_cores')} redis_cores={cg.get('perf-redis-master',{}).get('avg_cores')} "
           f"hostCPU={host['avg']} hit_ratio={hit_ratio}")
    return res

def append_csv(r):
    head = "run_id,cache,tps,achieved_rps,fail_rate,p50,p95,p99,host_cpu_avg,app_cores,mysql_cores,redis_cores,redis_hit_ratio\n"
    if not os.path.exists(CSV): open(CSV, "w").write(head)
    k = r["k6"]; cg = r["cgroup"]
    g = lambda x: "" if x is None else x
    row = [r["run_id"], r["cache"], r["tps"], g(k.get("achieved_rps")), g(k.get("fail_rate")),
           g(k.get("dur_med")), g(k.get("dur_p95")), g(k.get("dur_p99")), g(r["host_cpu"]["avg"]),
           g(cg.get("perf-app", {}).get("avg_cores")), g(cg.get("perf-mysql", {}).get("avg_cores")),
           g(cg.get("perf-redis-master", {}).get("avg_cores")), g(r["redis"]["hit_ratio"])]
    open(CSV, "a").write(",".join(str(x) for x in row) + "\n")

def main():
    rm.set_index("indexing")
    for cache in ("off", "on"):
        recreate_app(cache)
        rm.log("웜업 1분(JIT)…"); k6_global("warmup", 1000, None); time.sleep(2)
        for tps in TPS_LIST:
            run(cache, tps)
    rm.log("캐시 실험 완료")

if __name__ == "__main__":
    main()

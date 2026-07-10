#!/usr/bin/env python3
"""matrix 종료 후 실행. runs/*.json 을 집계해 ANALYSIS.md 생성 (인덱스 전후·웜업·포화·자원)."""
import json, os, glob

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RUNS = os.path.join(ROOT, "load-test/results/week5-l5/runs")
OUT = os.path.join(ROOT, "load-test/results/week5-l5/ANALYSIS.md")

def load():
    d = {}
    for f in glob.glob(os.path.join(RUNS, "*.json")):
        if f.endswith(".k6.json"):
            continue
        r = json.load(open(f))
        d[r["run_id"]] = r
    return d

def g(r, *path, default=None):
    cur = r
    for p in path:
        if cur is None:
            return default
        cur = cur.get(p)
    return cur if cur is not None else default

def fmt(x, suf="", nd=1):
    if x is None:
        return "—"
    if isinstance(x, float):
        return f"{round(x, nd)}{suf}"
    return f"{x}{suf}"

def main():
    runs = load()
    L = []
    L.append("# Week5 L5 — 인덱스 적용 전후 성능 분석\n")
    L.append("> `runs/*.json` 자동 집계. 측정 환경·과정은 [RUNBOOK.md](RUNBOOK.md) 참조.\n")

    # 1) 인덱스 전후 핵심 비교 (warm 기준, TPS별)
    L.append("## 1. 인덱스 전후 비교 (warm, TPS별)\n")
    L.append("| TPS | 조건 | achieved RPS | 실패율 | p50(ms) | p95(ms) | p99(ms) | 호스트CPU | mysql코어 | app코어 |")
    L.append("|---|---|---|---|---|---|---|---|---|---|")
    for tps in (100, 1000, 3000):
        for state in ("baseline", "indexing"):
            r = runs.get(f"{state}-{tps}-warm")
            if not r:
                continue
            L.append("| {} | {} | {} | {} | {} | {} | {} | {} | {} | {} |".format(
                tps, state,
                fmt(g(r, "k6", "achieved_rps")), fmt(g(r, "k6", "fail_rate"), nd=3),
                fmt(g(r, "k6", "dur_med")), fmt(g(r, "k6", "dur_p95")), fmt(g(r, "k6", "dur_p99")),
                fmt(g(r, "host", "cpu_busy", "avg")),
                fmt(g(r, "cgroup", "perf-mysql", "avg_cores"), nd=2),
                fmt(g(r, "cgroup", "perf-app", "avg_cores"), nd=2)))

    # 2) 인덱스 speedup
    L.append("\n## 2. 인덱스 효과 (warm, baseline÷indexing)\n")
    L.append("| TPS | p95 baseline | p95 indexing | p95 배수 | achieved baseline | achieved indexing | 처리량 배수 |")
    L.append("|---|---|---|---|---|---|---|")
    for tps in (100, 1000, 3000):
        b = runs.get(f"baseline-{tps}-warm"); i = runs.get(f"indexing-{tps}-warm")
        if not b or not i:
            continue
        bp, ip = g(b, "k6", "dur_p95"), g(i, "k6", "dur_p95")
        ba, ia = g(b, "k6", "achieved_rps"), g(i, "k6", "achieved_rps")
        spd = round(bp/ip, 1) if (bp and ip) else None
        thr = round(ia/ba, 2) if (ba and ia) else None
        L.append(f"| {tps} | {fmt(bp)} | {fmt(ip)} | {fmt(spd,'x')} | {fmt(ba)} | {fmt(ia)} | {fmt(thr,'x')} |")

    # 3) 웜업 효과 (cold vs warm)
    L.append("\n## 3. 웜업 효과 (cold vs warm)\n")
    L.append("| 조건 | TPS | p95 cold | p95 warm | JIT Δ cold(ms) | JIT Δ warm(ms) | app코어 cold | app코어 warm |")
    L.append("|---|---|---|---|---|---|---|---|")
    for state in ("baseline", "indexing"):
        for tps in (100, 1000, 3000):
            c = runs.get(f"{state}-{tps}-cold"); w = runs.get(f"{state}-{tps}-warm")
            if not c or not w:
                continue
            L.append("| {} | {} | {} | {} | {} | {} | {} | {} |".format(
                state, tps, fmt(g(c, "k6", "dur_p95")), fmt(g(w, "k6", "dur_p95")),
                fmt(g(c, "jvm", "jit_compile_ms_delta"), nd=0), fmt(g(w, "jvm", "jit_compile_ms_delta"), nd=0),
                fmt(g(c, "cgroup", "perf-app", "avg_cores"), nd=2), fmt(g(w, "cgroup", "perf-app", "avg_cores"), nd=2)))

    # 4) 포화 곡선 (offered vs achieved)
    L.append("\n## 4. 포화: offered vs achieved (warm)\n")
    L.append("| 조건 | offered 100 | offered 1000 | offered 3000 |")
    L.append("|---|---|---|---|")
    for state in ("baseline", "indexing"):
        cells = []
        for tps in (100, 1000, 3000):
            r = runs.get(f"{state}-{tps}-warm")
            cells.append(fmt(g(r, "k6", "achieved_rps")) if r else "—")
        L.append(f"| {state} | {cells[0]} | {cells[1]} | {cells[2]} |")

    # 5) 자원 포화 (PSI / Hikari)
    L.append("\n## 5. 자원 포화 신호 (warm)\n")
    L.append("| 조건 | TPS | PSI cpu Δ(s) | Hikari pending max | GC pause Δ(s) |")
    L.append("|---|---|---|---|---|")
    for state in ("baseline", "indexing"):
        for tps in (100, 1000, 3000):
            r = runs.get(f"{state}-{tps}-warm")
            if not r:
                continue
            L.append("| {} | {} | {} | {} | {} |".format(
                state, tps, fmt(g(r, "host", "psi_cpu_waiting_delta_s")),
                fmt(g(r, "jvm", "hikari_pending_max"), nd=0), fmt(g(r, "jvm", "gc_pause_s_delta"), nd=2)))

    L.append(f"\n---\n전체 런: {len(runs)}개. 원본 런별 상세는 `runs/<run_id>.md`.\n")
    open(OUT, "w").write("\n".join(L))
    print(f"ANALYSIS.md 생성: {len(runs)}런 집계")

if __name__ == "__main__":
    main()

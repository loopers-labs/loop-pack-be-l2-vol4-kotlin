#!/usr/bin/env python3
# 08-load-test-design.html 의 head(CSS 전체)를 재사용해 09-load-test-results.html 조립 (toss-design 일관성).
import os
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "docs/week5/08-load-test-design.html")
OUT = os.path.join(ROOT, "docs/week5/09-load-test-results.html")

lines = open(SRC).read().split("\n")
# head + <body> + #bar + <div class="sheet"> 까지 (line index 0..144 → '<div class="sheet">')
head = []
for i, ln in enumerate(lines):
    head.append(ln)
    if ln.strip() == '<div class="sheet">':
        break
HEAD = "\n".join(head)

SCRIPT = """
</div>
<script>
  const bar=document.getElementById('bar');
  const onScroll=()=>{const h=document.documentElement;const sc=h.scrollTop/(h.scrollHeight-h.clientHeight);bar.style.width=(sc*100)+'%';};
  document.addEventListener('scroll',onScroll,{passive:true});onScroll();
  const obs=new IntersectionObserver((es)=>{es.forEach(e=>{if(e.isIntersecting){e.target.classList.add('in');obs.unobserve(e.target);}});},{threshold:.08,rootMargin:'0px 0px -40px 0px'});
  document.querySelectorAll('.fade').forEach(s=>obs.observe(s));
</script>
</body>
</html>"""

BODY = r"""

  <header class="cover">
    <div class="cov">
      <span class="badge"><span class="d"></span>Loopers · Week 5 · Load Test Results</span>
      <h1 class="title">인덱스 적용 전후<br><span class="hl">성능 측정 결과</span></h1>
      <p class="lede">N100 홈서버(4C/14Gi)에서 <b>상품 10만 · 좋아요 681만</b> 시드로 <b>좋아요순 목록 조회</b>를 측정. <b>인덱스 X/O × 100·1,000·3,000 TPS × 웜업 cold/warm = 12런</b>. 핵심 결과: 100 TPS에서 p95 <b>9,997ms → 13.9ms (719배)</b>, 실패율 <b>82% → 0%</b>. 캐시는 별도 라운드.</p>
      <div class="chips">
        <div class="chip"><span class="k">시드</span><span class="v">product 100,750 · like 6,814,195</span></div>
        <div class="chip"><span class="k">타깃</span><span class="v">N100 4C · app+MySQL+Redis docker</span></div>
        <div class="chip"><span class="k">부하</span><span class="v">k6(Mac) · 10s 타임아웃 · 3분/런</span></div>
        <div class="chip"><span class="k">측정</span><span class="v">k6 · cgroup v2 · node PSI · Actuator</span></div>
      </div>
    </div>
  </header>

  <div class="docnav">
    <a href="00-overview.html"><span class="n">00</span>개요</a>
    <a href="04-index-explain.html"><span class="n">04</span>인덱스·EXPLAIN</a>
    <a href="07-pagination-benchmark.html"><span class="n">07</span>벤치마크</a>
    <a href="08-load-test-design.html"><span class="n">08</span>부하 설계</a>
    <a class="cur" href="09-load-test-results.html"><span class="n">09</span>측정 결과</a>
  </div>

  <nav class="toc">
    <h2>목차</h2>
    <ol>
      <li><a href="#s1">한눈에 보는 결과</a></li>
      <li><a href="#s2">측정 환경 · 방법</a></li>
      <li><a href="#s3">인덱스 전후 (핵심)</a></li>
      <li><a href="#s4">EXPLAIN 구조 변화</a></li>
      <li><a href="#s5">JVM 웜업 효과</a></li>
      <li><a href="#s6">포화: 목표 vs 달성</a></li>
      <li><a href="#s7">자원 — 병목의 이동</a></li>
      <li><a href="#s8">측정 신뢰성 · 교정</a></li>
    </ol>
  </nav>

  <main>

  <!-- ===== S1 ===== -->
  <section id="s1" class="fade">
    <div class="sec-head"><div class="sec-num">1</div>
      <div><p class="sec-eyebrow">TL;DR</p><h2 class="sec">한눈에 보는 결과</h2></div></div>
    <p class="lead">좋아요순 정렬에 복합 인덱스 하나를 추가하자, 같은 4코어 박스의 처리 천장이 약 <strong>10배</strong> 올라가고 응답 지연이 <strong>두 자릿수 ms</strong>로 떨어졌다.</p>
    <div class="cols">
      <div class="col x"><h4><span class="mk">BEFORE</span>인덱스 없음</h4>
        <p>100 TPS도 못 버팀 — 매 요청 <b>10만 행 풀스캔 + filesort</b>. p95 <b>9,997ms</b>, 실패 <b>82%</b>, MySQL이 <b>3.6코어</b>를 정렬에 소모. 병목 = DB.</p></div>
      <div class="col o"><h4><span class="mk">AFTER</span>복합 인덱스</h4>
        <p>인덱스 순서 그대로 읽어 <b>정렬 제거</b>. 100 TPS p95 <b>13.9ms</b>·실패 <b>0%</b>, 천장 <b>~950 TPS</b>(warm). 병목 = 앱 CPU로 이동.</p></div>
    </div>
    <div class="note key"><span class="tag">핵심 수치 (warm 기준)</span>
      <strong>100 TPS</strong> p95 9,997ms→13.9ms (<strong>719×</strong>) · <strong>1,000 TPS</strong> 달성 132→978 rps (<strong>7.4×</strong>) · <strong>3,000 TPS</strong> 달성 285→937 rps · 실패율 전 구간 <strong>0%대</strong>로.</div>
  </section>

  <!-- ===== S2 ===== -->
  <section id="s2" class="fade">
    <div class="sec-head"><div class="sec-num">2</div>
      <div><p class="sec-eyebrow">Environment</p><h2 class="sec">측정 환경 · 방법</h2></div></div>
    <p class="lead">단일 4코어 홈서버에 app·MySQL·Redis를 <em>CPU 제한 없이</em> 도커로 동거시켜 코어 경쟁을 그대로 관찰했다. 부하는 Mac에서 공인 IP로 주입.</p>
    <div class="tbl-wrap"><table>
      <thead><tr><th>구분</th><th>내용</th></tr></thead>
      <tbody>
        <tr><td>SUT</td><td>Intel N100 4C/4T · 14GiB · Ubuntu 26.04 · Docker 29 (containerd snapshotter)</td></tr>
        <tr><td>스택</td><td>commerce-api(temurin:21-jre, 힙 1g) + MySQL 8.0(buffer pool 2G) + Redis 7 ×2. kafka 제외</td></tr>
        <tr><td>데이터</td><td>product 100,750 · product_like 6,814,195 · brand 500 (멱법칙 4티어). 불변식 like_count=COUNT 검증</td></tr>
        <tr><td>부하</td><td>k6 v1.7.1(Mac) → <code>GET /api/v1/products?sort=LIKES_DESC</code> (70% 글로벌 / 30% 브랜드필터), <b>요청 10s 타임아웃</b>, 측정 3분/런</td></tr>
        <tr><td>측정</td><td>k6 summary · <b>cgroup v2 cpu.stat</b>(컨테이너별 CPU-초) · node_exporter(호스트 CPU·<b>PSI</b>) · Actuator(JIT·GC·Hikari)</td></tr>
      </tbody>
    </table></div>
    <div class="note"><span class="tag">왜 cgroup 직접인가</span>Docker 29 = containerd 스냅샷터라 <strong>cAdvisor 컨테이너 디스커버리가 실패</strong>. cAdvisor가 읽는 원천인 <code>cpu.stat usage_usec</code>를 직접 샘플링 — 런별 CPU-초 델타라 오히려 더 정확. CPU 제한 미설정이라 throttling 대신 <strong>호스트 PSI</strong>로 포화를 본다.</div>
  </section>

  <!-- ===== S3 ===== -->
  <section id="s3" class="fade">
    <div class="sec-head"><div class="sec-num">3</div>
      <div><p class="sec-eyebrow">Index ON/OFF · core result</p><h2 class="sec">인덱스 전후 (핵심)</h2></div></div>
    <p class="lead">warm(웜업 후) 기준, 같은 TPS에서 baseline과 indexing을 직접 비교한다.</p>
    __SVG_A__
    <div class="tbl-wrap"><table>
      <thead><tr><th>TPS</th><th>조건</th><th>달성 RPS</th><th>실패율</th><th>p50</th><th>p95</th><th>p99</th><th>호스트CPU</th></tr></thead>
      <tbody>
        <tr><td>100</td><td>baseline</td><td>92.6</td><td>82.0%</td><td>9,993ms</td><td>9,997ms</td><td>10,000ms</td><td>100%</td></tr>
        <tr><td>100</td><td>indexing</td><td>100.0</td><td>0%</td><td>10.7ms</td><td><b>13.9ms</b></td><td>18.5ms</td><td>21%</td></tr>
        <tr><td>1000</td><td>baseline</td><td>131.6</td><td>99.2%</td><td>9,702ms</td><td>10,036ms</td><td>239,474ms</td><td>100%</td></tr>
        <tr><td>1000</td><td>indexing</td><td><b>978.2</b></td><td>0.2%</td><td>12.1ms</td><td>781ms</td><td>4,191ms</td><td>80%</td></tr>
        <tr><td>3000</td><td>baseline</td><td>285.4</td><td>100%</td><td>9,070ms</td><td>9,996ms</td><td>10,000ms</td><td>100%</td></tr>
        <tr><td>3000</td><td>indexing</td><td><b>937.2</b></td><td>0%</td><td>3,222ms</td><td>4,253ms</td><td>4,667ms</td><td>99%</td></tr>
      </tbody>
    </table></div>
    <div class="note ok"><span class="tag">읽는 법</span>baseline의 p95가 9,000~10,000ms에 붙은 건 <strong>대부분 요청이 10초 타임아웃에 걸렸다</strong>는 뜻(=프론트 관점 실패). indexing은 100 TPS에서 14ms, 1,000 TPS에서도 사실상 무실패로 978 rps를 처리.</div>
  </section>

  <!-- ===== S4 ===== -->
  <section id="s4" class="fade">
    <div class="sec-head"><div class="sec-num">4</div>
      <div><p class="sec-eyebrow">EXPLAIN ANALYZE</p><h2 class="sec">EXPLAIN 구조 변화</h2></div></div>
    <p class="lead">왜 빨라졌나 — 실행계획이 <em>Table scan + Sort</em>에서 <em>Index scan</em>으로 바뀌며 정렬 단계가 통째로 사라졌다.</p>
    <div class="cols">
      <div class="col x"><h4><span class="mk">baseline</span>글로벌 첫 페이지</h4>
        <p><b>Table scan</b> on product (100,750행) → <b>Sort</b> like_count DESC, id DESC<br>cost=10,186 · 실측 <b>52.3ms</b></p></div>
      <div class="col o"><h4><span class="mk">indexing</span>글로벌 첫 페이지</h4>
        <p><b>Index scan</b> using idx_p_lc_id (reverse) · <b>Sort 없음</b><br>cost=<b>1.03</b> · 실측 <b>0.106ms</b></p></div>
    </div>
    <div class="tbl-wrap"><table>
      <thead><tr><th>쿼리</th><th>baseline 계획 / 실측</th><th>indexing 계획 / 실측</th></tr></thead>
      <tbody>
        <tr><td>글로벌 정렬</td><td>Table scan + Sort · 52.3ms</td><td>Index scan idx_p_lc_id (reverse) · <b>0.106ms</b></td></tr>
        <tr><td>브랜드 필터</td><td>Table scan + Sort · 40.3ms</td><td>Index lookup idx_p_brand_lc_id · <b>0.45ms</b></td></tr>
        <tr><td>keyset 깊은 페이지</td><td>Table scan + Sort · 55.0ms</td><td>Index range scan idx_p_lc_id · <b>0.51ms</b></td></tr>
      </tbody>
    </table></div>
    <div class="note key"><span class="tag">인덱스</span><code>idx_p_lc_id (like_count, id)</code> — 글로벌/keyset 정렬용 · <code>idx_p_brand_lc_id (brand_id, like_count, id)</code> — 브랜드 필터 + 정렬용. 둘 다 정렬 키 순서로 저장돼 <strong>filesort를 제거</strong>한다.</div>
  </section>

  <!-- ===== S5 ===== -->
  <section id="s5" class="fade">
    <div class="sec-head"><div class="sec-num">5</div>
      <div><p class="sec-eyebrow">JVM Warmup</p><h2 class="sec">JVM 웜업 효과</h2></div></div>
    <p class="lead">웜업은 <strong>CPU 바운드일 때만</strong> 의미가 있었다. 인덱싱 1,000 TPS에서 cold와 warm의 p95가 <strong>10배</strong> 갈렸다.</p>
    <div class="tbl-wrap"><table>
      <thead><tr><th>조건</th><th>TPS</th><th>p95 cold</th><th>p95 warm</th><th>JIT 컴파일 Δ cold</th><th>JIT Δ warm</th><th>달성 cold→warm</th></tr></thead>
      <tbody>
        <tr><td>indexing</td><td>1000</td><td>8,345ms</td><td><b>781ms</b></td><td><b>384,319ms</b></td><td>19,535ms</td><td>614 → 978</td></tr>
        <tr><td>indexing</td><td>3000</td><td>9,033ms</td><td><b>4,253ms</b></td><td>387,640ms</td><td>358,257ms</td><td>617 → 937</td></tr>
        <tr><td>baseline</td><td>1000</td><td>9,995ms</td><td>10,036ms</td><td>60,142ms</td><td>21,346ms</td><td>287 → 132</td></tr>
        <tr><td>baseline</td><td>3000</td><td>9,996ms</td><td>9,996ms</td><td>54,320ms</td><td>18,169ms</td><td>288 → 285</td></tr>
      </tbody>
    </table></div>
    <div class="note"><span class="tag">해석</span>indexing 1,000 cold는 측정 3분 동안 <strong>JIT 컴파일에만 384초</strong>를 썼다(여러 스레드 합산) — 부하를 받으며 핫패스를 컴파일하느라 p95가 8.3초까지 치솟음. warm은 웜업 1분에 컴파일을 끝내 781ms. <strong>baseline은 cold≈warm</strong> — 병목이 MySQL filesort라 JVM 웜업이 묻힌다. → "웜업이 필요한가"는 <strong>DB 바운드냐 CPU 바운드냐</strong>에 달렸다.</div>
  </section>

  <!-- ===== S6 ===== -->
  <section id="s6" class="fade">
    <div class="sec-head"><div class="sec-num">6</div>
      <div><p class="sec-eyebrow">Saturation</p><h2 class="sec">포화: 목표 vs 달성</h2></div></div>
    <p class="lead">목표 TPS를 올려도 달성 TPS가 따라오지 못하는 지점이 <em>포화 무릎</em>이다. 인덱스가 이 무릎을 약 10배 밀어냈다.</p>
    __SVG_B__
    <div class="note key"><span class="tag">무릎의 이동</span>baseline은 100 TPS부터 이미 천장 — 목표를 3,000까지 올려도 달성은 <strong>~90~285 rps</strong>에 묶이고 나머지는 타임아웃. indexing은 <strong>~950 rps</strong>까지 선형에 가깝게 따라오다 앱 CPU(3코어)에서 천장. 단일 박스 capacity가 <strong>~10배</strong> 늘었다.</div>
  </section>

  <!-- ===== S7 ===== -->
  <section id="s7" class="fade">
    <div class="sec-head"><div class="sec-num">7</div>
      <div><p class="sec-eyebrow">USE · resource</p><h2 class="sec">자원 — 병목의 이동</h2></div></div>
    <p class="lead">인덱스는 부하를 없앤 게 아니라 <strong>병목을 MySQL에서 앱 CPU로 옮겼다</strong>. cgroup으로 컨테이너별 코어 소모를 보면 명확하다.</p>
    __SVG_C__
    <div class="tbl-wrap"><table>
      <thead><tr><th>조건 (1000 TPS warm)</th><th>app 코어</th><th>mysql 코어</th><th>PSI cpu Δ</th><th>Hikari pending</th></tr></thead>
      <tbody>
        <tr><td>baseline</td><td>0.20</td><td><b>2.49</b></td><td>282s</td><td>160 (풀 고갈)</td></tr>
        <tr><td>indexing</td><td><b>2.01</b></td><td>0.98</td><td>85s</td><td>83</td></tr>
      </tbody>
    </table></div>
    <div class="note"><span class="tag">읽는 법</span>baseline은 MySQL이 정렬로 2.5코어를 태우고 커넥션 풀(40)이 꽉 차 160개가 대기(요청 적체). indexing은 DB가 0.98코어로 한가해지고, 대신 <strong>앱이 응답 직렬화·요청 처리로 2코어</strong>를 쓴다. 다음 개선의 타깃이 <strong>DB가 아니라 앱/캐시</strong>임을 자원 데이터가 가리킨다.</div>
  </section>

  <!-- ===== S8 ===== -->
  <section id="s8" class="fade">
    <div class="sec-head"><div class="sec-num">8</div>
      <div><p class="sec-eyebrow">Validity</p><h2 class="sec">측정 신뢰성 · 교정</h2></div></div>
    <p class="lead">측정을 유의미하게 만들기 위해 진행 중 두 가지를 교정했다. 둘 다 리포트의 측정 설계 근거다.</p>
    <div class="tbl-wrap"><table>
      <thead><tr><th>문제</th><th>원인</th><th>교정</th></tr></thead>
      <tbody>
        <tr><td>baseline 3000 런이 36분 소요 · p95 1,084,672ms · 호스트CPU 음수</td><td>k6 요청 타임아웃 부재 → 포화 시 Tomcat 큐에 무한 적체 = "무한 큐잉 아티팩트"</td><td><b>요청 10s 타임아웃</b>(axios 관례 + Nielsen 10초 주의 한계) + gracefulStop → 포화를 "10s SLA 위반율"로 측정</td></tr>
        <tr><td>컨테이너별 CPU 수집 불가</td><td>Docker 29 containerd 스냅샷터와 cAdvisor 비호환</td><td><b>cgroup v2 cpu.stat 직접 샘플링</b>으로 전환 (원천 동일·런별 정확)</td></tr>
      </tbody>
    </table></div>
    <div class="note ok"><span class="tag">남은 일</span>이번은 <strong>인덱스 전용</strong>. 자원 데이터가 다음 병목으로 <strong>앱 CPU</strong>를 지목 → 캐시(Redis read path) 라운드에서 목록/상세 캐싱 + 무효화 전략을 같은 매트릭스로 측정 예정.</div>
  </section>

  </main>

  <footer>
    <span class="gl">⁂</span>
    Loopers Week 5 — 인덱스 전후 측정 결과 · 12런(2 인덱스 × 3 TPS × 2 웜업)<br>
    N100 단일 박스 · k6 10s 타임아웃 · cgroup v2 · node PSI · Actuator · 캐시 별도 라운드
  </footer>
"""

# ---- SVG A: p95 비교 (로그 수평바, 100/1000/3000) ----
def logx(v):
    import math
    return 150 + math.log10(max(v, 1)) * 132  # 1ms→150, 10000ms→678

rows_a = [("100 TPS", 9997, 13.9), ("1,000 TPS", 10036, 781), ("3,000 TPS", 9996, 4253)]
svg_a = ['<figure class="fig"><svg viewBox="0 0 760 300" role="img" aria-label="인덱스 전후 p95 비교 (로그 스케일)" style="width:100%;height:auto">']
svg_a.append('<rect x="0" y="0" width="760" height="300" fill="#F9FAFB" rx="16"/>')
# x grid (10,100,1000,10000 ms)
for ms, lab in [(10,"10ms"),(100,"100ms"),(1000,"1s"),(10000,"10s")]:
    x = logx(ms)
    svg_a.append(f'<line x1="{x:.0f}" y1="40" x2="{x:.0f}" y2="250" stroke="#E5E8EB" stroke-width="1"/>')
    svg_a.append(f'<text x="{x:.0f}" y="270" font-size="11" fill="#8B95A1" text-anchor="middle" font-family="JetBrains Mono">{lab}</text>')
y = 56
for lab, b, i in rows_a:
    svg_a.append(f'<text x="20" y="{y+24:.0f}" font-size="12.5" fill="#333D4B" font-weight="700" font-family="JetBrains Mono">{lab}</text>')
    xb = logx(b); xi = logx(i)
    svg_a.append(f'<rect x="150" y="{y:.0f}" width="{xb-150:.0f}" height="18" rx="4" fill="#F04452" opacity="0.85"/>')
    svg_a.append(f'<text x="{xb+8:.0f}" y="{y+14:.0f}" font-size="11" fill="#C9303C" font-weight="700" font-family="JetBrains Mono">{b:,}ms</text>')
    svg_a.append(f'<rect x="150" y="{y+26:.0f}" width="{xi-150:.0f}" height="18" rx="4" fill="#3182F6"/>')
    lab_i = f'{i}ms' if i < 100 else f'{int(i):,}ms'
    svg_a.append(f'<text x="{xi+8:.0f}" y="{y+40:.0f}" font-size="11" fill="#1B64DA" font-weight="700" font-family="JetBrains Mono">{lab_i}</text>')
    y += 64
svg_a.append('<rect x="600" y="20" width="12" height="12" rx="3" fill="#F04452" opacity="0.85"/><text x="618" y="30" font-size="11" fill="#4E5968" font-family="JetBrains Mono">baseline</text>')
svg_a.append('<rect x="690" y="20" width="12" height="12" rx="3" fill="#3182F6"/><text x="708" y="30" font-size="11" fill="#4E5968" font-family="JetBrains Mono">index</text>')
svg_a.append('</svg><figcaption style="text-align:center;font-size:13px;color:#8B95A1;margin-top:8px;font-family:JetBrains Mono">p95 응답시간 (warm · 로그 스케일)</figcaption></figure>')
SVG_A = "\n".join(svg_a)

# ---- SVG B: 포화 곡선 (offered vs achieved) ----
xs = {100: 150, 1000: 400, 3000: 640}
def ay(v): return 290 - (v/1000.0)*230  # 0→290, 1000→60
base = {100: 92.6, 1000: 131.6, 3000: 285.4}
idx = {100: 100.0, 1000: 978.2, 3000: 937.2}
svg_b = ['<figure class="fig"><svg viewBox="0 0 720 340" role="img" aria-label="목표 TPS 대비 달성 TPS 포화 곡선" style="width:100%;height:auto">']
svg_b.append('<rect x="0" y="0" width="720" height="340" fill="#F9FAFB" rx="16"/>')
for v, lab in [(0,"0"),(250,"250"),(500,"500"),(750,"750"),(1000,"1000")]:
    yy = ay(v)
    svg_b.append(f'<line x1="120" y1="{yy:.0f}" x2="680" y2="{yy:.0f}" stroke="#E5E8EB" stroke-width="1"/>')
    svg_b.append(f'<text x="110" y="{yy+4:.0f}" font-size="10.5" fill="#8B95A1" text-anchor="end" font-family="JetBrains Mono">{lab}</text>')
for tps in (100,1000,3000):
    svg_b.append(f'<text x="{xs[tps]}" y="312" font-size="11.5" fill="#6B7684" text-anchor="middle" font-family="JetBrains Mono">{tps:,}</text>')
svg_b.append('<text x="400" y="332" font-size="11" fill="#8B95A1" text-anchor="middle" font-family="JetBrains Mono">목표 TPS (offered)</text>')
# baseline line
pts_b = " ".join(f"{xs[t]},{ay(base[t]):.0f}" for t in (100,1000,3000))
svg_b.append(f'<polyline points="{pts_b}" fill="none" stroke="#F04452" stroke-width="2.5" opacity="0.85"/>')
pts_i = " ".join(f"{xs[t]},{ay(idx[t]):.0f}" for t in (100,1000,3000))
svg_b.append(f'<polyline points="{pts_i}" fill="none" stroke="#3182F6" stroke-width="2.5"/>')
for t in (100,1000,3000):
    svg_b.append(f'<circle cx="{xs[t]}" cy="{ay(base[t]):.0f}" r="4" fill="#F04452"/>')
    svg_b.append(f'<circle cx="{xs[t]}" cy="{ay(idx[t]):.0f}" r="4" fill="#3182F6"/>')
svg_b.append(f'<text x="{xs[1000]}" y="{ay(978)-12:.0f}" font-size="11" fill="#1B64DA" font-weight="700" text-anchor="middle" font-family="JetBrains Mono">978</text>')
svg_b.append(f'<text x="{xs[3000]}" y="{ay(937)-12:.0f}" font-size="11" fill="#1B64DA" font-weight="700" text-anchor="middle" font-family="JetBrains Mono">937</text>')
svg_b.append(f'<text x="{xs[3000]}" y="{ay(285)+18:.0f}" font-size="11" fill="#C9303C" font-weight="700" text-anchor="middle" font-family="JetBrains Mono">285</text>')
svg_b.append('<rect x="150" y="30" width="12" height="12" rx="3" fill="#3182F6"/><text x="168" y="40" font-size="11" fill="#4E5968" font-family="JetBrains Mono">indexing</text>')
svg_b.append('<rect x="270" y="30" width="12" height="12" rx="3" fill="#F04452" opacity="0.85"/><text x="288" y="40" font-size="11" fill="#4E5968" font-family="JetBrains Mono">baseline</text>')
svg_b.append('<text x="120" y="40" font-size="11" fill="#8B95A1" font-family="JetBrains Mono">달성 RPS</text>')
svg_b.append('</svg><figcaption style="text-align:center;font-size:13px;color:#8B95A1;margin-top:8px;font-family:JetBrains Mono">목표를 올려도 baseline은 천장에 묶이고 indexing은 ~950까지 따라온다 (warm)</figcaption></figure>')
SVG_B = "\n".join(svg_b)

# ---- SVG C: 병목 이동 (코어, 1000 TPS warm) ----
svg_c = ['<figure class="fig"><svg viewBox="0 0 720 280" role="img" aria-label="병목 이동: 컨테이너별 CPU 코어 소모" style="width:100%;height:auto">']
svg_c.append('<rect x="0" y="0" width="720" height="280" fill="#F9FAFB" rx="16"/>')
def cy(v): return 220 - (v/4.0)*170  # 0→220, 4core→50
for v in (0,1,2,3,4):
    yy = cy(v)
    svg_c.append(f'<line x1="150" y1="{yy:.0f}" x2="670" y2="{yy:.0f}" stroke="#E5E8EB" stroke-width="1"/>')
    svg_c.append(f'<text x="140" y="{yy+4:.0f}" font-size="10.5" fill="#8B95A1" text-anchor="end" font-family="JetBrains Mono">{v}c</text>')
groups = [("baseline", 250, 0.20, 2.49), ("indexing", 470, 2.01, 0.98)]
for lab, cx, app, mysql in groups:
    svg_c.append(f'<rect x="{cx-55}" y="{cy(app):.0f}" width="50" height="{220-cy(app):.0f}" rx="4" fill="#3182F6"/>')
    svg_c.append(f'<text x="{cx-30}" y="{cy(app)-7:.0f}" font-size="11" fill="#1B64DA" font-weight="700" text-anchor="middle" font-family="JetBrains Mono">{app}</text>')
    svg_c.append(f'<rect x="{cx+5}" y="{cy(mysql):.0f}" width="50" height="{220-cy(mysql):.0f}" rx="4" fill="#15C47E"/>')
    svg_c.append(f'<text x="{cx+30}" y="{cy(mysql)-7:.0f}" font-size="11" fill="#0A8F5B" font-weight="700" text-anchor="middle" font-family="JetBrains Mono">{mysql}</text>')
    svg_c.append(f'<text x="{cx}" y="242" font-size="12.5" fill="#333D4B" font-weight="700" text-anchor="middle" font-family="JetBrains Mono">{lab}</text>')
svg_c.append('<rect x="150" y="30" width="12" height="12" rx="3" fill="#3182F6"/><text x="168" y="40" font-size="11" fill="#4E5968" font-family="JetBrains Mono">app</text>')
svg_c.append('<rect x="230" y="30" width="12" height="12" rx="3" fill="#15C47E"/><text x="248" y="40" font-size="11" fill="#4E5968" font-family="JetBrains Mono">mysql</text>')
svg_c.append('<text x="640" y="40" font-size="11" fill="#8B95A1" text-anchor="end" font-family="JetBrains Mono">1000 TPS warm</text>')
svg_c.append('</svg><figcaption style="text-align:center;font-size:13px;color:#8B95A1;margin-top:8px;font-family:JetBrains Mono">병목이 MySQL(2.49코어)에서 앱(2.01코어)으로 이동</figcaption></figure>')
SVG_C = "\n".join(svg_c)

body = BODY.replace("__SVG_A__", SVG_A).replace("__SVG_B__", SVG_B).replace("__SVG_C__", SVG_C)
open(OUT, "w").write(HEAD + body + SCRIPT)
print("생성:", OUT)

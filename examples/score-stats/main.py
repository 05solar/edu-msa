"""성적 통계 계산기 — 점수 목록의 평균·최고·최저·표준편차·등급 분포를 계산한다."""
import json
import math
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PAGE = """<!doctype html><html lang="ko"><meta charset="utf-8">
<title>성적 통계 계산기</title>
<style>
body{font-family:system-ui,"Malgun Gothic",sans-serif;max-width:760px;margin:36px auto;padding:0 16px;color:#101828}
h1{font-size:22px;margin-bottom:4px} p.d{color:#667085;margin-top:0}
textarea{width:100%;padding:10px;border:1px solid #d0d5dd;border-radius:6px;font:inherit;box-sizing:border-box}
button{margin-top:12px;padding:10px 18px;border:0;border-radius:6px;background:#1d4ed8;color:#fff;font-weight:700;cursor:pointer}
table{border-collapse:collapse;margin-top:16px;width:100%}
th,td{border:1px solid #e4e7ec;padding:8px 10px;text-align:left}th{background:#f7f8fa}
.big{font-size:20px;font-weight:800}
</style>
<h1>성적 통계 계산기</h1>
<p class="d">점수를 쉼표나 줄바꿈으로 구분해 입력하세요.</p>
<textarea id="scores" rows="4">88, 92, 76, 61, 95, 84, 73, 100, 58, 80</textarea>
<button onclick="run()">계산</button>
<div id="out"></div>
<script>
async function run(){
  const r=await fetch('/api/stats',{method:'POST',body:scores.value});
  const d=await r.json();
  if(d.count===0){out.innerHTML='<p>숫자를 입력하세요.</p>';return;}
  out.innerHTML=`<table>
   <tr><th>개수</th><td>${d.count}</td><th>평균</th><td class="big">${d.mean}</td></tr>
   <tr><th>최고</th><td>${d.max}</td><th>최저</th><td>${d.min}</td></tr>
   <tr><th>중앙값</th><td>${d.median}</td><th>표준편차</th><td>${d.stddev}</td></tr>
  </table>
  <table><tr><th>A(90+)</th><th>B(80+)</th><th>C(70+)</th><th>D(60+)</th><th>F(60미만)</th></tr>
   <tr><td>${d.grades.A}</td><td>${d.grades.B}</td><td>${d.grades.C}</td><td>${d.grades.D}</td><td>${d.grades.F}</td></tr></table>`;
}
run();
</script></html>"""


def compute(text):
    nums = [float(x) for x in re.findall(r"-?\d+(?:\.\d+)?", text or "")]
    if not nums:
        return {"count": 0}
    n = len(nums)
    mean = sum(nums) / n
    var = sum((x - mean) ** 2 for x in nums) / n
    s = sorted(nums)
    median = s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2
    grades = {"A": 0, "B": 0, "C": 0, "D": 0, "F": 0}
    for x in nums:
        grades["A" if x >= 90 else "B" if x >= 80 else "C" if x >= 70 else "D" if x >= 60 else "F"] += 1
    r2 = lambda v: round(v, 2)
    return {"count": n, "mean": r2(mean), "max": r2(max(nums)), "min": r2(min(nums)),
            "median": r2(median), "stddev": r2(math.sqrt(var)), "grades": grades}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="text/plain; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _json(self, obj):
        self._send(200, json.dumps(obj, ensure_ascii=False), "application/json; charset=utf-8")

    def do_GET(self):  # noqa: N802
        u = urlparse(self.path)
        if u.path.startswith("/healthz"):
            self._send(200, "ok")
        elif u.path == "/":
            self._send(200, PAGE, "text/html; charset=utf-8")
        elif u.path == "/api/stats":
            self._json(compute(parse_qs(u.query).get("scores", [""])[0]))
        else:
            self._send(404, "not found")

    def do_POST(self):  # noqa: N802
        if not self.path.startswith("/api/stats"):
            self._send(404, "not found"); return
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode("utf-8")
        self._json(compute(body))

    def log_message(self, *a):
        print(self.address_string(), a[0] % a[1:])


def main():
    port = int(os.environ.get("PORT", "8080"))
    print(f"score-stats listening on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()

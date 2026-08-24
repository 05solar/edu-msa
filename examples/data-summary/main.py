"""데이터 요약 통계 — 붙여넣은 숫자(CSV/공백/줄바꿈)의 기초 통계를 계산한다."""
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PAGE = """<!doctype html><html lang="ko"><meta charset="utf-8">
<title>데이터 요약 통계</title>
<style>
body{font-family:system-ui,"Malgun Gothic",sans-serif;max-width:760px;margin:36px auto;padding:0 16px;color:#101828}
h1{font-size:22px;margin-bottom:4px} p.d{color:#667085;margin-top:0}
textarea{width:100%;padding:10px;border:1px solid #d0d5dd;border-radius:6px;font:inherit;box-sizing:border-box}
button{margin-top:12px;padding:10px 18px;border:0;border-radius:6px;background:#1d4ed8;color:#fff;font-weight:700;cursor:pointer}
table{border-collapse:collapse;margin-top:16px;width:100%}th,td{border:1px solid #e4e7ec;padding:10px;text-align:left}th{background:#f7f8fa}
</style>
<h1>데이터 요약 통계</h1>
<p class="d">엑셀에서 복사한 숫자를 그대로 붙여넣어도 됩니다. (쉼표·공백·줄바꿈 구분)</p>
<textarea id="raw" rows="6">12, 45, 33, 27, 88, 19, 54, 60, 41, 73, 22, 95</textarea>
<button onclick="run()">요약</button>
<div id="out"></div>
<script>
async function run(){
  const d=await(await fetch('/api/summary',{method:'POST',body:raw.value})).json();
  if(!d.count){out.innerHTML='<p>숫자를 입력하세요.</p>';return;}
  out.innerHTML=`<table>
   <tr><th>개수</th><td>${d.count}</td><th>합계</th><td>${d.sum}</td></tr>
   <tr><th>평균</th><td>${d.mean}</td><th>중앙값</th><td>${d.median}</td></tr>
   <tr><th>최소</th><td>${d.min}</td><th>최대</th><td>${d.max}</td></tr></table>`;
}
run();
</script></html>"""


def compute(text):
    nums = [float(x) for x in re.findall(r"-?\d+(?:\.\d+)?", text or "")]
    if not nums:
        return {"count": 0}
    n = len(nums)
    s = sorted(nums)
    median = s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2
    r = lambda v: int(v) if float(v) == int(v) else round(v, 2)
    return {"count": n, "sum": r(sum(nums)), "mean": round(sum(nums) / n, 2),
            "median": r(median), "min": r(min(nums)), "max": r(max(nums))}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="text/plain; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):  # noqa: N802
        if self.path.startswith("/healthz"):
            self._send(200, "ok")
        elif self.path == "/":
            self._send(200, PAGE, "text/html; charset=utf-8")
        else:
            self._send(404, "not found")

    def do_POST(self):  # noqa: N802
        if not self.path.startswith("/api/summary"):
            self._send(404, "not found"); return
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode("utf-8")
        self._send(200, json.dumps(compute(body), ensure_ascii=False), "application/json; charset=utf-8")

    def log_message(self, *a):
        print(self.address_string(), a[0] % a[1:])


def main():
    port = int(os.environ.get("PORT", "8080"))
    print(f"data-summary listening on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()

"""교육과정 시수 계산기 — 주당 시수 × 수업 주수로 학기/연간 시수를 계산한다."""
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PAGE = """<!doctype html><html lang="ko"><meta charset="utf-8">
<title>교육과정 시수 계산기</title>
<style>
body{font-family:system-ui,"Malgun Gothic",sans-serif;max-width:640px;margin:36px auto;padding:0 16px;color:#101828}
h1{font-size:22px;margin-bottom:4px} p.d{color:#667085;margin-top:0}
label{display:block;font-weight:600;margin:12px 0 4px}
input{width:100%;padding:8px 10px;border:1px solid #d0d5dd;border-radius:6px;font:inherit;box-sizing:border-box}
button{margin-top:14px;padding:10px 18px;border:0;border-radius:6px;background:#1d4ed8;color:#fff;font-weight:700;cursor:pointer}
table{border-collapse:collapse;margin-top:16px;width:100%}th,td{border:1px solid #e4e7ec;padding:10px}th{background:#f7f8fa}
td.b{font-size:20px;font-weight:800}
</style>
<h1>교육과정 시수 계산기</h1>
<p class="d">주당 시수와 연간 수업 주수를 입력하세요.</p>
<label>주당 시수</label><input id="weekly" type="number" value="3">
<label>연간 수업 주수</label><input id="weeks" type="number" value="34">
<button onclick="run()">계산</button>
<div id="out"></div>
<script>
async function run(){
  const q=new URLSearchParams({weekly:weekly.value,weeks:weeks.value});
  const d=await(await fetch('/api/hours?'+q)).json();
  out.innerHTML=`<table>
   <tr><th>연간 총 시수</th><td class="b">${d.annual} 시간</td></tr>
   <tr><th>학기당 시수(주수 절반 기준)</th><td>${d.per_semester} 시간</td></tr>
   <tr><th>계산식</th><td>${d.weekly} × ${d.weeks}</td></tr></table>`;
}
run();
</script></html>"""


def compute(p):
    def num(k):
        try:
            return float(p.get(k, ["0"])[0])
        except ValueError:
            return 0.0
    weekly, weeks = num("weekly"), num("weeks")
    annual = weekly * weeks
    r = lambda v: int(v) if v == int(v) else round(v, 1)
    return {"weekly": r(weekly), "weeks": r(weeks), "annual": r(annual), "per_semester": r(annual / 2)}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="text/plain; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):  # noqa: N802
        u = urlparse(self.path)
        if u.path.startswith("/healthz"):
            self._send(200, "ok")
        elif u.path == "/":
            self._send(200, PAGE, "text/html; charset=utf-8")
        elif u.path == "/api/hours":
            self._send(200, json.dumps(compute(parse_qs(u.query)), ensure_ascii=False), "application/json; charset=utf-8")
        else:
            self._send(404, "not found")

    def log_message(self, *a):
        print(self.address_string(), a[0] % a[1:])


def main():
    port = int(os.environ.get("PORT", "8080"))
    print(f"class-hours listening on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()

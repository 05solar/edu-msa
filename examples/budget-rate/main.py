"""예산 집행률 계산기 — 예산액 대비 집행액으로 집행률·잔액·상태를 계산한다."""
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PAGE = """<!doctype html><html lang="ko"><meta charset="utf-8">
<title>예산 집행률 계산기</title>
<style>
body{font-family:system-ui,"Malgun Gothic",sans-serif;max-width:640px;margin:36px auto;padding:0 16px;color:#101828}
h1{font-size:22px;margin-bottom:4px} p.d{color:#667085;margin-top:0}
label{display:block;font-weight:600;margin:12px 0 4px}
input{width:100%;padding:8px 10px;border:1px solid #d0d5dd;border-radius:6px;font:inherit;box-sizing:border-box}
button{margin-top:14px;padding:10px 18px;border:0;border-radius:6px;background:#1d4ed8;color:#fff;font-weight:700;cursor:pointer}
.bar{height:16px;background:#edeff3;border-radius:20px;overflow:hidden;margin-top:16px}
.bar i{display:block;height:100%;background:#1d4ed8}
table{border-collapse:collapse;margin-top:14px;width:100%}th,td{border:1px solid #e4e7ec;padding:10px}th{background:#f7f8fa}
td.b{font-size:20px;font-weight:800}
</style>
<h1>예산 집행률 계산기</h1>
<p class="d">예산액과 집행액을 입력하세요.</p>
<label>예산액(원)</label><input id="budget" type="number" value="10000000">
<label>집행액(원)</label><input id="spent" type="number" value="6500000">
<button onclick="run()">계산</button>
<div class="bar"><i id="fill" style="width:0"></i></div>
<div id="out"></div>
<script>
async function run(){
  const q=new URLSearchParams({budget:budget.value,spent:spent.value});
  const d=await(await fetch('/api/rate?'+q)).json();
  fill.style.width=Math.min(100,d.rate)+'%';
  out.innerHTML=`<table>
   <tr><th>집행률</th><td class="b">${d.rate}%</td><th>상태</th><td>${d.status}</td></tr>
   <tr><th>집행액</th><td>${d.spent.toLocaleString()}원</td><th>잔액</th><td>${d.remaining.toLocaleString()}원</td></tr>
  </table>`;
}
run();
</script></html>"""


def compute(p):
    def num(k):
        try:
            return float(p.get(k, ["0"])[0])
        except ValueError:
            return 0.0
    budget, spent = num("budget"), num("spent")
    rate = (spent / budget * 100) if budget else 0
    status = "정상" if rate >= 50 else "집행 저조"
    if rate > 100:
        status = "예산 초과"
    return {"budget": int(budget), "spent": int(spent), "remaining": int(budget - spent),
            "rate": round(rate, 1), "status": status}


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
        elif u.path == "/api/rate":
            self._send(200, json.dumps(compute(parse_qs(u.query)), ensure_ascii=False), "application/json; charset=utf-8")
        else:
            self._send(404, "not found")

    def log_message(self, *a):
        print(self.address_string(), a[0] % a[1:])


def main():
    port = int(os.environ.get("PORT", "8080"))
    print(f"budget-rate listening on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()

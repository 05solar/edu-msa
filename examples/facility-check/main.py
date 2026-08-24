"""시설 점검 체크리스트 — 표준 점검 항목 대비 완료율과 미점검 항목을 정리한다."""
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

ITEMS = ["소화기 비치", "비상구 표시등", "전기 배선 상태", "조명 정상 작동",
         "바닥 미끄럼 방지", "창문·잠금장치", "응급처치함 비치"]

PAGE = """<!doctype html><html lang="ko"><meta charset="utf-8">
<title>시설 점검 체크리스트</title>
<style>
body{font-family:system-ui,"Malgun Gothic",sans-serif;max-width:640px;margin:36px auto;padding:0 16px;color:#101828}
h1{font-size:22px;margin-bottom:4px} p.d{color:#667085;margin-top:0}
label{display:flex;gap:8px;align-items:center;padding:9px 0;border-bottom:1px solid #eff1f4;font-size:15px}
button{margin-top:14px;padding:10px 18px;border:0;border-radius:6px;background:#1d4ed8;color:#fff;font-weight:700;cursor:pointer}
#out{margin-top:16px;font-size:15px}.miss{color:#b42318}
</style>
<h1>시설 점검 체크리스트</h1>
<p class="d">점검을 완료한 항목에 체크한 뒤 결과 정리를 누르세요.</p>
<div id="list"></div>
<button onclick="run()">결과 정리</button>
<div id="out"></div>
<script>
const ITEMS=%ITEMS%;
list.innerHTML=ITEMS.map((n,i)=>`<label><input type=checkbox value="${n}" ${i<4?'checked':''}> ${n}</label>`).join('');
async function run(){
  const checked=[...document.querySelectorAll('input:checked')].map(x=>x.value);
  const q=new URLSearchParams();checked.forEach(c=>q.append('checked',c));
  const d=await(await fetch('/api/check?'+q)).json();
  out.innerHTML=`<b>완료율 ${d.rate}% (${d.done}/${d.total})</b> · 판정: ${d.pass?'적합':'보완 필요'}`
    +(d.missing.length?`<div class="miss">미점검: ${d.missing.join(', ')}</div>`:'');
}
run();
</script></html>""".replace("%ITEMS%", json.dumps(ITEMS, ensure_ascii=False))


def compute(p):
    checked = set(p.get("checked", []))
    missing = [i for i in ITEMS if i not in checked]
    done = len(ITEMS) - len(missing)
    return {"total": len(ITEMS), "done": done, "missing": missing,
            "rate": round(done / len(ITEMS) * 100), "pass": not missing}


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
        elif u.path == "/api/check":
            self._send(200, json.dumps(compute(parse_qs(u.query)), ensure_ascii=False), "application/json; charset=utf-8")
        else:
            self._send(404, "not found")

    def log_message(self, *a):
        print(self.address_string(), a[0] % a[1:])


def main():
    port = int(os.environ.get("PORT", "8080"))
    print(f"facility-check listening on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()

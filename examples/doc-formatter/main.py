"""기안문 서식 생성기 — 입력 항목을 표준 기안문 형식으로 정리한다. (표준 라이브러리만 사용)"""
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PAGE = """<!doctype html><html lang="ko"><meta charset="utf-8">
<title>기안문 서식 생성기</title>
<style>
body{font-family:system-ui,"Malgun Gothic",sans-serif;max-width:760px;margin:36px auto;padding:0 16px;color:#101828}
h1{font-size:22px;margin-bottom:4px} p.d{color:#667085;margin-top:0}
label{display:block;font-weight:600;margin:12px 0 4px}
input,textarea{width:100%;padding:8px 10px;border:1px solid #d0d5dd;border-radius:6px;font:inherit;box-sizing:border-box}
button{margin-top:14px;padding:10px 18px;border:0;border-radius:6px;background:#1d4ed8;color:#fff;font-weight:700;cursor:pointer}
pre{white-space:pre-wrap;background:#0d1b2a;color:#d6e2f0;padding:16px;border-radius:8px;margin-top:16px;line-height:1.7}
</style>
<h1>기안문 서식 생성기</h1>
<p class="d">항목을 입력하면 표준 기안문 형식으로 정리해 줍니다.</p>
<label>제목</label><input id="title" value="2026년 상반기 업무 추진 계획 보고">
<label>부서</label><input id="dept" value="행정지원과">
<label>기안자</label><input id="writer" value="홍길동">
<label>본문</label><textarea id="body" rows="4">상반기 업무 추진 계획을 붙임과 같이 보고합니다.</textarea>
<button onclick="gen()">서식 생성</button>
<pre id="out"></pre>
<script>
async function gen(){
  const q=new URLSearchParams({title:title.value,dept:dept.value,writer:writer.value,body:body.value});
  const r=await fetch('/api/format?'+q); out.textContent=await r.text();
}
gen();
</script></html>"""


def build_doc(p):
    g = lambda k: (p.get(k, [""])[0]).strip()
    line = "─" * 42
    return (
        f"{g('dept')}\n{line}\n"
        f"제  목 : {g('title')}\n\n"
        f"{g('body')}\n\n"
        f"{line}\n"
        f"기안자 : {g('writer')} (인)\n"
        f"협  조 : \n결  재 : \n"
    )


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
        elif u.path == "/api/format":
            self._send(200, build_doc(parse_qs(u.query)))
        else:
            self._send(404, "not found")

    def log_message(self, *a):
        print(self.address_string(), a[0] % a[1:])


def main():
    port = int(os.environ.get("PORT", "8080"))
    print(f"doc-formatter listening on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()

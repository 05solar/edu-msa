"""민원 답변 초안 생성기 — 유형·민원인·내용으로 정중한 답변 초안 텍스트를 만든다."""
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

CLOSING = {
    "시설": "말씀해 주신 시설 관련 사항은 담당 부서에 전달하여 현장 확인 후 조치하겠습니다.",
    "학사": "학사 운영과 관련한 사항은 관련 규정을 확인하여 안내드리겠습니다.",
    "인사": "인사 관련 문의는 담당 부서에서 검토 후 별도로 안내드리겠습니다.",
    "기타": "문의하신 사항은 담당 부서에서 확인 후 성실히 답변드리겠습니다.",
}

PAGE = """<!doctype html><html lang="ko"><meta charset="utf-8">
<title>민원 답변 초안 생성기</title>
<style>
body{font-family:system-ui,"Malgun Gothic",sans-serif;max-width:760px;margin:36px auto;padding:0 16px;color:#101828}
h1{font-size:22px;margin-bottom:4px} p.d{color:#667085;margin-top:0}
label{display:block;font-weight:600;margin:12px 0 4px}
input,textarea,select{width:100%;padding:8px 10px;border:1px solid #d0d5dd;border-radius:6px;font:inherit;box-sizing:border-box}
button{margin-top:14px;padding:10px 18px;border:0;border-radius:6px;background:#1d4ed8;color:#fff;font-weight:700;cursor:pointer}
pre{white-space:pre-wrap;background:#0d1b2a;color:#d6e2f0;padding:16px;border-radius:8px;margin-top:16px;line-height:1.8}
</style>
<h1>민원 답변 초안 생성기</h1>
<p class="d">유형과 내용을 입력하면 정중한 답변 초안을 만들어 줍니다. (발송 전 담당자 검토 필요)</p>
<label>민원 유형</label>
<select id="type"><option>시설</option><option>학사</option><option>인사</option><option>기타</option></select>
<label>민원인 성함</label><input id="name" value="홍길동">
<label>민원 내용</label><textarea id="content" rows="3">운동장 야간 조명이 어두워 이용에 불편이 있습니다.</textarea>
<button onclick="gen()">답변 초안 생성</button>
<pre id="out"></pre>
<script>
async function gen(){
  const q=new URLSearchParams({type:type.value,name:name.value,content:content.value});
  out.textContent=await(await fetch('/api/reply?'+q)).text();
}
gen();
</script></html>"""


def build_reply(p):
    g = lambda k: (p.get(k, [""])[0]).strip()
    t = g("type") or "기타"
    name = g("name") or "민원인"
    closing = CLOSING.get(t, CLOSING["기타"])
    return (
        f"{name} 님께\n\n"
        f"안녕하십니까. 소중한 의견을 주셔서 감사합니다.\n\n"
        f"문의하신 내용(\"{g('content')}\")을 잘 확인하였습니다. "
        f"{closing}\n\n"
        f"추가로 궁금하신 사항이 있으시면 언제든지 문의해 주시기 바랍니다. 감사합니다.\n\n"
        f"담당 부서 드림"
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
        elif u.path == "/api/reply":
            self._send(200, build_reply(parse_qs(u.query)))
        else:
            self._send(404, "not found")

    def log_message(self, *a):
        print(self.address_string(), a[0] % a[1:])


def main():
    port = int(os.environ.get("PORT", "8080"))
    print(f"civil-reply listening on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()

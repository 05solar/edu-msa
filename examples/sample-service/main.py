"""
표준 서비스 규격 예제 (VIBE_CODING_GUIDE.md / MSA_SERVICE_SPEC.md 준수).

- 환경변수 PORT 포트로 HTTP 서버를 연다 (하드코딩 금지).
- GET /healthz 는 200 "ok" 를 반환한다.
- GET / 는 간단한 화면을 반환한다.
외부 라이브러리 없이 표준 라이브러리만 사용한다.
"""
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def settle(days: int, nights: int, transport: int) -> dict:
    """국내 출장 여비를 아주 단순화해 계산한다 (예시)."""
    per_diem = 25000 * days          # 일비
    meals = 25000 * days             # 식비
    lodging = 70000 * nights         # 숙박비
    total = per_diem + meals + lodging + transport
    return {
        "per_diem": per_diem, "meals": meals,
        "lodging": lodging, "transport": transport, "total": total,
    }


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, body: str, content_type: str = "text/plain; charset=utf-8") -> None:
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self) -> None:  # noqa: N802
        if self.path.startswith("/healthz"):
            self._send(200, "ok")
        elif self.path == "/" or self.path.startswith("/?"):
            html = (
                "<!doctype html><meta charset='utf-8'>"
                "<title>출장 정산 자동 계산기</title>"
                "<h1>출장 정산 자동 계산기</h1>"
                "<p>표준 서비스 규격 예제입니다. /api/settle?days=2&nights=1&transport=48000 로 계산합니다.</p>"
            )
            self._send(200, html, "text/html; charset=utf-8")
        elif self.path.startswith("/api/settle"):
            from urllib.parse import parse_qs, urlparse
            q = parse_qs(urlparse(self.path).query)
            result = settle(
                int(q.get("days", ["1"])[0]),
                int(q.get("nights", ["0"])[0]),
                int(q.get("transport", ["0"])[0]),
            )
            self._send(200, json.dumps(result, ensure_ascii=False), "application/json; charset=utf-8")
        else:
            self._send(404, "not found")

    def log_message(self, *args):  # 로그를 stdout으로 (플랫폼이 수집)
        print("%s - %s" % (self.address_string(), args[0] % args[1:]))


def main() -> None:
    port = int(os.environ.get("PORT", "8080"))
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"sample-service listening on :{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()

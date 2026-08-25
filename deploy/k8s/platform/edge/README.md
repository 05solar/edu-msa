# 엣지 보안 (TLS + Rate-limit + WAF)

불특정 다수(미신뢰)가 접근하는 외부 경계다. ingress-nginx에서 TLS 종료,
서비스별 rate-limit, ModSecurity + OWASP CRS(WAF)로 유해 요청을 차단한다.

## 설치 (helm)
```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update ingress-nginx
helm install ingress-nginx ingress-nginx/ingress-nginx -n ingress-nginx \
  --create-namespace -f ingress-nginx-values.yaml
```
`ingress-nginx-values.yaml`가 ModSecurity + OWASP CRS를 전역 활성화(SecRuleEngine On)한다.

## TLS
- 실서버: **cert-manager** + ClusterIssuer(Let's Encrypt 등)로 호스트별 인증서 자동 발급,
  또는 와일드카드 인증서 시크릿. Ingress `spec.tls`에 `secretName` 지정.
- 프로토콜 TLSv1.2/1.3만 허용(values).

## Rate-limit (서비스별)
Ingress 주석으로 지정 — 서비스 템플릿에 기본값 포함:
```
nginx.ingress.kubernetes.io/limit-rps: "20"
nginx.ingress.kubernetes.io/limit-connections: "10"
```

## WAF (ModSecurity + OWASP CRS)
- 컨트롤러 전역 활성화. Ingress별로도 명시 가능:
```
nginx.ingress.kubernetes.io/enable-modsecurity: "true"
nginx.ingress.kubernetes.io/enable-owasp-core-rules: "true"
```
- 미신뢰(공개) 서비스(`edu-services-public`)에 특히 권장.

## 동작 검증 (kind, 2026-08-25)
컨트롤러 ClusterIP로 `--resolve` 하여 클러스터 내부에서 검증:
- **TLS 종료**: 정상 HTTPS 요청 → **200**, 서빙 인증서 `CN=edu-waf.internal`(발급 시크릿 사용 확인).
- **WAF 차단**: XSS(`<script>`) → **403**, SQLi(`1' OR '1'='1 --`) → **403**, 경로탐색(`../../etc/passwd`) → **403**.
- **정상 요청**(`?name=hong`) → **200** (오탐 없이 통과).
- **Rate-limit**: `limit-rps:1` 로 20연속 요청 시 초과분 **503** 반환(강제 확인).

## 남은 항목(프로덕션)
- cert-manager 자동 인증서, WAF 규칙 튜닝(오탐 예외), CRS 파라노이아 레벨 조정.
- CDN/DDoS 완화(엣지 앞단), 봇 차단, 감사로그 연동.

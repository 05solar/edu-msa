# 자동 TLS 인증서 (cert-manager)

P2-3 엣지의 수동 인증서를 **cert-manager**로 자동화한다. Ingress에 주석 한 줄이면
인증서 발급·시크릿 생성·갱신이 자동으로 이뤄진다.

## 설치 (helm)
```bash
helm repo add jetstack https://charts.jetstack.io && helm repo update jetstack
helm install cert-manager jetstack/cert-manager -n cert-manager --create-namespace \
  --set crds.enabled=true
```

## 발급자 체인 (`clusterissuers.yaml`)
- `selfsigned` (ClusterIssuer): 자체 서명 부트스트랩.
- `edu-root-ca` (Certificate, isCA): 10년짜리 자체 루트 CA → 시크릿 `edu-root-ca`.
- `edu-ca` (ClusterIssuer, ca): 위 루트 CA로 서비스 인증서 서명.
```bash
kubectl apply -f clusterissuers.yaml
```

> 실서버 인터넷 서비스는 `edu-ca` 대신 **ACME(Let's Encrypt) ClusterIssuer**로 교체해
> 공인 인증서를 자동 발급한다(HTTP-01/DNS-01). 내부망/오프라인은 self-signed 루트 CA 사용.

## 서비스 적용 (ingress-shim)
서비스 Ingress에 주석 + `spec.tls`만 있으면 cert-manager가 인증서를 자동 생성한다.
(테넌트 서비스 템플릿에 기본 포함)
```yaml
metadata:
  annotations:
    cert-manager.io/cluster-issuer: edu-ca
spec:
  tls:
    - hosts: [ <host> ]
      secretName: <slug>-tls   # cert-manager가 자동 생성/갱신
```

## 동작 검증 (kind, 2026-08-25)
- 발급자: `selfsigned`/`edu-ca` ClusterIssuer, `edu-root-ca` Certificate 모두 **Ready=True**.
- 명시적 Certificate(`edu-waf-auto`) → 시크릿 자동 생성, **issuer=CN=edu-msa-root-ca**, SAN `edu-waf.internal`.
- **ingress-shim**: 주석(`cert-manager.io/cluster-issuer: edu-ca`)만 단 Ingress →
  약 3초 내 Certificate **Ready=True**, 시크릿 `shim-app-tls` 자동 생성(우리 CA 서명, SAN 일치).
- 결론: Ingress 주석만으로 TLS 인증서 자동 발급·갱신 체계 동작 확인.

## 남은 항목(프로덕션)
- ACME 발급자(Let's Encrypt) + 해결기(Ingress/DNS) 구성, 인증서 만료 모니터링(Prometheus).
- 루트 CA 키 보관(HSM/Vault), 신뢰 배포(내부 CA를 클라이언트 신뢰 저장소에 등록).

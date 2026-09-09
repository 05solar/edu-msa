# deploy/k8s/secrets · 운영 Secret 관리 (Sealed Secrets)

매니페스트에는 **어떤 자리표시자 Secret 도 없다.** 로컬/리허설(kind)은 `bootstrap.sh` 가
무작위 값으로 자동 생성하고, **운영(server)은 Secret 이 사전에 없으면 bootstrap 이 중단**한다
— `change-me` 류 값이 운영에 올라갈 경로 자체가 없다.

## 선택: Sealed Secrets

외부 비밀 저장소(Vault·클라우드 SM)가 없는 내부망 자체 구축 환경을 전제로,
**Bitnami Sealed Secrets** 를 표준으로 한다. 클러스터 공개키로 암호화한
`*.sealed.yaml` 만 Git 에 커밋할 수 있고, 복호화는 클러스터 안의 컨트롤러만 가능하다.
(클라우드 SM 을 쓰는 환경이면 External Secrets Operator 로 대체해도 앱 쪽은 바뀌지 않는다 —
앱은 Secret 이름/키만 바라본다.)

```bash
# 컨트롤러 설치(운영 클러스터 1회)
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm upgrade --install sealed-secrets sealed-secrets/sealed-secrets -n kube-system
# kubeseal CLI: https://github.com/bitnami-labs/sealed-secrets/releases
```

## 필수 Secret 목록 (edu-platform 네임스페이스)

| Secret | 키 | 용도 |
|---|---|---|
| `edu-db` | POSTGRES_DB=edumsa · POSTGRES_USER=edumsa · POSTGRES_PASSWORD | 플랫폼 DB(개발용 단일 postgres 경로) |
| `edu-auth-db` | POSTGRES_DB=eduauth · POSTGRES_USER=eduauth · POSTGRES_PASSWORD | 인증 DB(개발용 단일 auth-db 경로) |
| `edu-auth-jwt` | EDU_JWT_SECRET(≥32B) · EDU_SEED_PASSWORD | JWT 서명 키(auth 발급/backend 검증 공유) |
| `edu-redis-auth` | password | Redis 캐시·rate-limit 카운터 |
| `edu-db-backup-creds` | ACCESS_KEY_ID · ACCESS_SECRET_KEY | CNPG 백업 오브젝트 스토리지(HA 사용 시) |
| (CNPG 자동 생성) `edu-db-app` · `edu-auth-db-app` | username · password | HA DB 앱 계정 — 직접 만들지 않는다 |
| (bootstrap 자동) `gitea-admin` · `edu-gitea-token` · `edu-gitea-webhook` | — | Gitea 연동 |

## 운영 반영 절차

```bash
# 1) 평문 Secret 을 로컬 파일로 생성 (Git 에 절대 커밋하지 않는다 — .gitignore 처리됨)
kubectl -n edu-platform create secret generic edu-auth-jwt \
  --from-literal=EDU_JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=EDU_SEED_PASSWORD="$(openssl rand -base64 12)" \
  --dry-run=client -o yaml > edu-auth-jwt.yaml

# 2) 클러스터 공개키로 봉인 → sealed 파일만 Git 커밋 가능
kubeseal --format yaml < edu-auth-jwt.yaml > edu-auth-jwt.sealed.yaml
rm edu-auth-jwt.yaml

# 3) 적용 — 컨트롤러가 복호화해 실제 Secret 을 만든다
kubectl apply -f edu-auth-jwt.sealed.yaml
```

나머지 Secret 도 동일하다. 키 구성은 `edu-secrets.yaml.template` 참고
(`.template` 확장자라 kubectl apply 대상이 아니며, 값을 채워도 직접 apply 하지 말고 반드시 봉인한다).

## 회전(rotation)

새 값으로 1)~3) 재수행 후 소비자 재기동:
```bash
kubectl -n edu-platform rollout restart deploy/auth-service deploy/backend
```
JWT 시크릿 회전 시 기존 Access Token(30분)은 만료까지 유효하지 않게 되므로
사용량이 낮은 시간대에 수행한다.

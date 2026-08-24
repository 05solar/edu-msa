# MSA 표준 서비스 규격 · "기본 프로그램은 이래야 한다"

이 문서는 바이브 코더가 올린 임의의 GitHub 레포지토리가 교육청 플랫폼에서 하나의
MSA 서비스로 배포되기 위해 만족해야 하는 **기술 계약(contract)**을 정의한다.
쉬운 안내는 [VIBE_CODING_GUIDE.md](VIBE_CODING_GUIDE.md)를 참고한다.

## 1. 배포 파이프라인 개요

```
GitHub 레포 주소 등록
   → 코드 수집 (git clone, 지정 브랜치)
   → 규격 검증 (service.yaml / Dockerfile / 헬스 경로 정적 검사)
   → 컨테이너 이미지 빌드 (레포의 Dockerfile 사용)
   → 이미지 레지스트리 push
   → K8s 배포 (Deployment + Service + Ingress 경로 /svc/<slug>)
   → 헬스 체크 통과 시 "공개" 상태로 전환
```

## 2. 필수 산출물 (레포 루트)

### 2.1 `service.yaml` (필수)

```yaml
name: string          # 필수. 표시 이름
slug: string          # 필수. ^[a-z][a-z0-9-]{1,38}$ (K8s/URL 식별자)
category: enum        # 필수. doc|student|curri|budget|facil|data|civil
purposes: [enum]      # 선택. auto|gen|verify|analyze|summary|search|dash
tech: [string]        # 선택. 자유 표기
summary: string       # 선택. 한 줄 소개 (<= 120자)
port: int             # 필수. 컨테이너가 listen 하는 포트 (1024-65535)
health: string        # 선택. 기본 "/healthz"
env: {KEY: value}     # 선택. 추가 환경변수 (민감정보 금지)
resources:            # 선택. 미지정 시 기본값 적용
  cpu: "250m"
  memory: "256Mi"
```

### 2.2 `Dockerfile` (필수)

- 레포 루트에 존재해야 한다.
- 최종 이미지는 `PORT` 환경변수로 지정된 포트에서 HTTP 요청을 받아야 한다.
- 루트가 아닌 사용자로 실행하는 것을 권장한다.
- 멀티스테이지 빌드를 권장한다(이미지 크기·보안).

## 3. 런타임 계약

| 항목 | 요구사항 |
| --- | --- |
| 포트 | 환경변수 `PORT` (플랫폼이 주입). 코드에 하드코딩 금지. |
| 헬스 체크 | `GET {health}` → HTTP 200. 미지정 시 `/healthz`. |
| 무상태 권장 | 컨테이너는 언제든 재시작될 수 있음. 상태는 외부(DB/오브젝트 스토리지)에. |
| 로그 | stdout/stderr로 출력 (플랫폼이 수집). |
| 시작 시간 | 30초 이내에 헬스 체크 통과 권장. |

## 4. 정적 검증 규칙 (등록 시 자동 검사)

등록 요청 시 플랫폼이 아래를 검사하고 실패 항목을 반려 사유로 표시한다.

1. `service.yaml` 존재 및 YAML 파싱 성공
2. `name`, `slug`, `category`, `port` 필수값 존재 및 형식 일치
3. `slug` 정규식 및 전역 유일성
4. `category`가 허용 enum
5. `Dockerfile` 존재
6. 레포 접근 가능(공개 또는 등록된 토큰으로)
7. 헬스 경로 문자열이 `/`로 시작

## 5. K8s 배포 매핑 (플랫폼 내부)

각 서비스는 `slug`를 기준으로 아래 리소스를 생성한다. (상세는 Phase 3에서 구현)

- `Deployment/<slug>` — 레포 이미지, `PORT` 주입, 리소스 제한, 헬스 프로브
- `Service/<slug>` — ClusterIP, 대상 포트 = `port`
- `Ingress` 경로 `/{slug}` 또는 서브도메인 → 플랫폼 게이트웨이 라우팅
- `namespace: edu-services`

readiness/liveness probe는 `service.yaml`의 `health` 경로를 사용한다.

## 6. 보안·정책

- 이미지 실행은 비루트, 읽기 전용 루트 파일시스템 권장.
- 아웃바운드 네트워크는 내부망 정책에 따름(기본 제한).
- 시크릿은 `service.yaml`에 넣지 않는다. 플랫폼 시크릿 관리로 주입.
- 개인정보 포함 데이터는 레포에 커밋하지 않는다.

## 7. 버전과 업데이트

- 새 버전은 같은 레포에 push 후 플랫폼에서 "업데이트 요청" → 재빌드/재배포.
- 롤백을 위해 직전 이미지 태그를 보관한다.

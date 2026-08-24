# 아키텍처 설계 · edu-msa

## 1. 목표

교육청 구성원이 만든 프로그램을 GitHub 레포로 올리면, 플랫폼이 자동으로 MSA
서비스로 배포해 다른 직원이 웹에서 바로 사용하게 한다.

## 2. 구성 요소

```
                         ┌─────────────────────────────┐
   사용자 (브라우저) ──▶ │  프론트엔드 (React+Vite+TSX)  │
                         └───────────────┬─────────────┘
                                         │ REST/JSON
                         ┌───────────────▼─────────────┐
                         │  플랫폼 API (Spring Boot 3)   │
                         │  - 프로그램 등록/조회/승인     │
                         │  - 배포 오케스트레이션 트리거   │
                         └───┬─────────────────┬────────┘
                             │                 │
                   ┌─────────▼──────┐   ┌──────▼───────────────┐
                   │ PostgreSQL     │   │  배포 파이프라인       │
                   │ 프로그램·사용자 │   │  clone→검증→build→push │
                   └────────────────┘   └──────┬───────────────┘
                                                │ kubectl/API
                                         ┌──────▼───────────────┐
                                         │  Kubernetes           │
                                         │  namespace: edu-services
                                         │  서비스별 Deploy/Svc/Ingress
                                         └──────────────────────┘
```

## 3. 계층별 책임

### 프론트엔드 (`frontend/`)
- 프로그램 탐색/검색/상세, 등록 폼, 내 프로그램, 운영 관리자 화면.
- 역할(일반 사용자/바이브 코더/운영 관리자)에 따른 메뉴·권한 분기.
- Phase 1은 목업 데이터로 동작. Phase 2에서 API 연동.

### 플랫폼 API (`backend/`)
- 도메인: `program`(등록/조회/버전), `review`(승인/반려), `user`(인증·권한),
  `deploy`(배포 오케스트레이션), `catalog`(분류 체계).
- 기능별 패키지 분리. PostgreSQL 영속화.

### 배포 파이프라인 (`backend/deploy` + `deploy/`)
- GitHub clone → `service.yaml`/`Dockerfile` 정적 검증 → 이미지 빌드 → 레지스트리
  push → K8s 매니페스트 생성/적용 → 헬스 확인 → 상태 전환.
- 표준 규격은 [MSA_SERVICE_SPEC.md](MSA_SERVICE_SPEC.md).

## 4. 데이터 모델 (초안)

- `program(id, name, slug, category, summary, description, repo_url, branch,
  owner_id, status, scope, version, created_at, updated_at)`
- `program_purpose(program_id, purpose)` · `program_tech(program_id, tech)`
- `program_version(id, program_id, version, changelog, image_tag, created_at)`
- `review(id, program_id, reviewer_id, action, memo, created_at)`
- `app_user(id, name, dept, role)` · `favorite(user_id, program_id)`
- `notification(id, to_user_id, kind, title, sub, program_id, read, created_at)`

`status`: draft | pending | public | rejected | stopped

## 5. 배포 상태 머신

```
draft ─(제출)→ pending ─(승인+배포성공)→ public
  pending ─(반려)→ rejected
  public ─(중지)→ stopped ─(재개)→ public
```

## 6. 기술 결정 (ADR 요약)

- 프론트: React 18 + Vite + TypeScript. 상태는 Context 기반 경량 스토어(외부
  상태 라이브러리 미도입).
- 백엔드: Spring Boot 3, Java 21, Gradle Kotlin DSL. Phase 2에서 확정.
- DB: PostgreSQL. 마이그레이션 도구는 Phase 2에서 결정(Flyway 유력).
- 오케스트레이션: Kubernetes. 서비스별 네임스페이스 격리(`edu-services`).
- 아이콘: 인라인 SVG 세트(이모지 금지).

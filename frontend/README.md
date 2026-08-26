# 프론트엔드 · 교육청 코드 공유 플랫폼

React + Vite + TypeScript(TSX)로 만든 포털 프론트엔드. 로그인(또는 데모 로그인) 후
이용하며, 기본적으로 백엔드 API(`auth-service`·`backend`)와 연동해 동작한다.
백엔드가 없을 때만 목업 데이터로 폴백한다.

## 실행

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # 타입체크 + 프로덕션 빌드
npm run typecheck  # 타입만 검사
```

## 화면

로그인 후 쓰는 주요 화면 7개와, 진입 전 인증 화면(`Auth/`)으로 구성한다.

- `home` 홈 — 검색 히어로, 업무 분야 바로가기, 즐겨찾기/인기/최근.
- `list` 프로그램 탐색 — 필터/정렬/검색. 목록은 로그인 역할로 필터된 `/api/programs`.
- `ai` AI로 프로그램 찾기 — 업무 설명 기반 추천(데모).
- `register` 프로그램 등록 — 등록 폼(바이브 코더/운영 관리자).
- `my` 내 프로그램 / 마이페이지 — 등록 현황·즐겨찾기·알림, "내 권한" 패널(상향 권한 신청/취소).
- `detail` 프로그램 상세 — 소개/사용법/업데이트/의견/AI, "웹에서 바로 사용".
- `admin` 운영 관리자 — 등록 검토·이력·현황·카테고리, "권한 요청" 탭(승인/반려).
- `Auth/` 로그인·회원가입·아이디 찾기·비밀번호 찾기.

## 백엔드 연동 모드

- 기본: `VITE_USE_API` 기본값이 `true`라 API 모드로 동작한다. Vite 프록시가
  `/api/auth` → `auth-service`(8089), `/api` → `backend`(8088)로 전달하고,
  등록/승인/댓글/알림/권한이 실제 API로 처리된다.
- 폴백: `VITE_USE_API=false`로 두거나 백엔드가 떠 있지 않으면 목업 데이터로 동작한다.
- 백엔드는 `docker compose -f ../deploy/docker-compose.yml up --build`로 띄운다
  (db·auth-db·auth-service·traefik·backend, `deploy/.env` 필요).

## 로그인 / 데모 / 권한

인증 화면은 `src/pages/Auth/` 에 있다. 로그인·회원가입은 `auth-service` 와 연동되며,
아이디 찾기·비밀번호 찾기는 화면과 입력값 검증까지 동작한다(메일 발송은 추후).

**회원가입은 항상 최소 권한(USER)** 으로 만들어진다. 바이브 코더/운영 관리자 권한은
가입 시 신청하거나 로그인 후 마이페이지 "내 권한"에서 신청하며, 운영 관리자의 승인이
있어야 실제로 상승한다(자가 신청만으로는 상승하지 않는다).

**데모로 시작하기** 버튼은 계정 입력 없이 진입한다. 이때도 실제 토큰을 발급받으므로
플랫폼 API 를 그대로 쓸 수 있다. 좌측 하단 "시연용 권한 전환"에서 일반 사용자 /
바이브 코더 / 운영 관리자 역할을 바꾸면 해당 역할의 데모 계정 토큰을 다시 받는다.

Access Token 은 메모리(`src/api/token.ts`)에만 두고 localStorage 에 저장하지 않는다.
`api/client.ts`가 요청에 Bearer 헤더를 붙이며, 새로고침 시에는 HttpOnly Refresh 쿠키
(`/api/auth/refresh`)로 세션을 복구한다.

## 폴더 구조

```
src/
├── main.tsx · App.tsx · App.css
├── styles/            # tokens.css · base.css
├── icons/             # Icon.tsx (인라인 SVG 세트, 이모지 대체)
├── types/             # 도메인 타입
├── data/              # 목업 데이터 (programs, categories, …)
├── state/             # AppContext (경량 스토어)
├── lib/               # 포맷/유틸
├── api/               # client.ts(플랫폼) · auth.ts(인증) · token.ts(토큰 보관)
├── components/        # Sidebar/ Topbar/ Footer/ Toast/ Modal/ …
└── pages/             # Home/ List/ Detail/ Register/ My/ Ai/ Admin/
                       #   Auth/ (로그인·회원가입·아이디 찾기·비밀번호 찾기)
```

각 페이지/컴포넌트 폴더는 `*.tsx`와 `*.css`를 함께 둔다. 자세한 규칙은
[DESIGN.md](DESIGN.md), [AGENT.md](AGENT.md) 참조.

## 필수 규칙

- 이모지 금지 (DESIGN.md 0절). 아이콘은 SVG만 사용.

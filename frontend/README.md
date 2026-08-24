# 프론트엔드 · 교육청 코드 공유 플랫폼

React + Vite + TypeScript(TSX)로 만든 포털 프론트엔드. Phase 1은 목업 데이터로
동작하는 데모이며, Phase 2에서 백엔드 API와 연동한다.

## 실행

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # 타입체크 + 프로덕션 빌드
npm run typecheck  # 타입만 검사
```

## 화면 (7)

- `home` 홈 — 검색 히어로, 업무 분야, 기능 유형 바로가기, 인기/최근.
- `list` 프로그램 탐색 — 필터/정렬/검색.
- `ai` AI로 프로그램 찾기 — 업무 설명 기반 추천(데모).
- `register` 프로그램 등록 — 등록 폼(바이브 코더/운영 관리자).
- `my` 내 프로그램 / 마이페이지 — 등록 현황·즐겨찾기·알림.
- `detail` 프로그램 상세 — 소개/사용법/업데이트/의견/AI.
- `admin` 운영 관리자 — 등록 검토·이력·현황·카테고리.

## 백엔드 연동 모드

- 기본(오프라인): 목업 데이터로 동작.
- 연동: `VITE_USE_API=true`로 실행하면 `/api`(→ 백엔드 8088 프록시)에서 데이터를 가져오고
  등록/승인/댓글/알림/권한이 실제 API로 처리된다. 백엔드는
  `docker compose -f ../deploy/docker-compose.yml up --build`로 띄운다.

## 데모 로그인 / 권한

인증은 화면만 있고 미구현이다. 데모로 진입하며, 좌측 하단 "시연용 권한 전환"에서
일반 사용자 / 바이브 코더 / 운영 관리자 역할을 바꾼다.

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
├── components/        # Sidebar/ Topbar/ Footer/ Toast/ Modal/ …
└── pages/             # Home/ List/ Detail/ Register/ My/ Ai/ Admin/ Login/
```

각 페이지/컴포넌트 폴더는 `*.tsx`와 `*.css`를 함께 둔다. 자세한 규칙은
[DESIGN.md](DESIGN.md), [AGENT.md](AGENT.md) 참조.

## 필수 규칙

- 이모지 금지 (DESIGN.md 0절). 아이콘은 SVG만 사용.

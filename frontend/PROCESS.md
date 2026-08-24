# PROCESS.md · 프론트엔드 진행 이력

## 프로세스

1. 요청 접수 → 관련 메타 문서 확인.
2. 페이지/컴포넌트 단위로 구현(폴더 분리, tsx+css).
3. `npm run typecheck` / `npm run build` / 데모 스모크 검증.
4. 본 이력과 관련 문서 갱신.

## 진행 이력 (Change Log)

- 2026-08-24 — Vite+React+TSX 스캐폴드, tsconfig/index.html/package.json 작성.
- 2026-08-24 — 디자인 시스템 CSS 이식(tokens/base), 인라인 SVG 아이콘 세트 작성.
- 2026-08-24 — 목업 데이터/도메인 타입/AppContext 상태·라우팅, 앱 셸(Sidebar/Topbar/Footer/Toast/Modal), 데모 로그인 페이지.
- 2026-08-24 — 7개 화면(Home/List/Detail/Register/My/Ai/Admin) 포팅 (이모지 → SVG).
- 2026-08-24 — 검증 완료: `tsc -b` 타입 오류 0, `vite build` 성공(66 모듈), preview 서버 HTTP 200 확인.
- 2026-08-24 — Phase 2 연동: 타입드 API 클라이언트(src/api/client.ts), Vite 프록시(/api→8088), AppContext를 API 모드 지원으로 확장(VITE_USE_API=true 시 백엔드 사용, 미설정 시 목업). 타입체크/빌드 통과, 프록시 경유 데이터 수신 확인.
- 2026-08-24 — Phase 3: 등록 화면 "레포 규격 검증" 버튼(+결과 표시), 운영 관리자 "배포" 액션(파이프라인 로그/매니페스트 모달, DeployResultModal), api 클라이언트에 validate/deploy 추가. 타입체크/빌드 통과, 프록시 경유 POST 확인.

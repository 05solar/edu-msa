# PROCESS.md · 프론트엔드 진행 이력

## 프로세스

1. 요청 접수 → 관련 메타 문서 확인.
2. 페이지/컴포넌트 단위로 구현(폴더 분리, tsx+css).
3. `npm run typecheck` / `npm run build` / 데모 스모크 검증.
4. 본 이력과 관련 문서 갱신.

## 진행 이력 (Change Log)

- 2026-09-11 — P2-1 알림 페이지네이션: api.notifications(page,size) 페이지 응답 + notiUnreadCount 신설, AppContext 가 첫 페이지(20건)만 로드하고 "더보기"(loadMoreNotis — id 기준 중복 방지 append)로 추가 로드, unread 배지는 서버 COUNT(read/read-all 시 로컬 감소·0), 사용자/권한 전환 시 page 0 부터 교체 로드. API 모드의 클라이언트 이름 필터 제거 — 역할 공지(to="운영 관리자")가 걸러지던 회귀 수정(서버가 uid|role 로 이미 필터). My 알림 탭에 더보기 버튼. tsc+vite build 통과, staging 스모크 확인.

- 2026-09-11 — P1-5 identity 반영: 프로그램 등록 시 owner 전송 제거(서버가 JWT 로 결정 — 보내도 무시됨), Program 타입에 ownerId 추가, "내 프로그램" 필터를 ownerId===account.id 우선(legacy·목업은 이름 폴백)으로 전환. tsc+vite build 통과, staging API 회귀 확인.

- 2026-09-11 — P1-1(알림 IDOR) API 계약 반영: `api.notifications()`·`api.readAllNotis()` 에서 `?to=` 사용자 식별자 전달 제거 — 서버가 JWT 로 현재 사용자를 판단한다(경로 불변). AppContext 의 두 호출부만 수정, 기능(목록·unread 배지·단일/전체 읽음·권한 전환 시 재조회) 불변. 검증: tsc + vite build 통과, staging API 로 본인 조회·읽음 정상 확인.
- 2026-09-09 — 정적 서빙 최적화(12단계): nginx.conf 에 gzip(vary·min 1k·level 5)과 자산별 Cache-Control — 해시 자산(/assets/*) 1년 immutable, HTML no-cache(항상 재검증 — CDN 앞단 전제), 비해시 정적(svg/png 등) 1일, guides 1시간. SPA fallback(try_files → index.html) 유지. brotli 는 nginx:alpine 모듈 부재로 미적용(ngx_brotli 이미지 또는 CDN 엣지 압축 권장 주석). 검증: nginx:1.27-alpine 실컨테이너로 nginx -t + 경로별 헤더·gzip·SPA 폴백(200/no-cache) 전수 확인.
- 2026-09-09 — 카탈로그 서버 페이지네이션 연동: 목록 API 가 페이지 응답(items/totalElements/totalPages)으로 바뀜에 따라 api.list 가 필터·검색·정렬·page/size 파라미터를 서버로 전달(ListParams·ProgramPage 타입), api.programCounts(분야별 개수) 추가, api.listAll 페이지 인자화. List 페이지는 서버 주도 조회(검색어 300ms 디바운스, 20건/페이지, 이전/다음 페이저)로 전환하고 오프라인 목업 모드는 기존 클라이언트 필터링 유지. AppContext 는 최근 100건 작업셋만 보관(Home/My/Admin 용). 검증: tsc·vite build 통과.
- 2026-09-03 — Gitea 3단계: 등록 화면 안내를 "내부 Gitea 주소(권장) 또는 GitHub 주소"로 갱신(placeholder·설명·오류 문구). 타입체크 통과.
- 2026-09-03 — 프로그램 삭제: api.deleteProgram + AppContext.deleteProgram(API/목업 겸용, 목록·알림·detailId 정리, 실패 토스트), 내 프로그램 표와 운영 관리자 전체 프로그램 탭에 확인창 딸린 삭제 버튼 추가. 검증: typecheck/빌드 통과, 목업 모드 E2E(내 프로그램 3→2행, 관리자 15→14행, 토스트·통계 갱신 확인).

- 2026-08-24 — Vite+React+TSX 스캐폴드, tsconfig/index.html/package.json 작성.
- 2026-08-24 — 디자인 시스템 CSS 이식(tokens/base), 인라인 SVG 아이콘 세트 작성.
- 2026-08-24 — 목업 데이터/도메인 타입/AppContext 상태·라우팅, 앱 셸(Sidebar/Topbar/Footer/Toast/Modal), 데모 로그인 페이지.
- 2026-08-24 — 7개 화면(Home/List/Detail/Register/My/Ai/Admin) 포팅 (이모지 → SVG).
- 2026-08-24 — 검증 완료: `tsc -b` 타입 오류 0, `vite build` 성공(66 모듈), preview 서버 HTTP 200 확인.
- 2026-08-24 — Phase 2 연동: 타입드 API 클라이언트(src/api/client.ts), Vite 프록시(/api→8088), AppContext를 API 모드 지원으로 확장(VITE_USE_API 기본 true=백엔드 사용, false거나 백엔드 미가동 시 목업 폴백). 타입체크/빌드 통과, 프록시 경유 데이터 수신 확인.
- 2026-08-24 — UI 조정: 사이드바 밝은 테마(흰 배경+잉크 텍스트, 활성 항목 브랜드 블루)로 변경, 홈 히어로 보조 문구 제거.
- 2026-08-24 — 장식용 라벨 제거(히어로 킥커/사이드바 DEMO/푸터 PoC/로그인 DEMO 플래그), 상태·분류 배지는 유지.
- 2026-08-24 — 테마/폰트 개편: Pretendard 폰트 적용, 라이트/다크 테마 토글(토큰화+localStorage), 전역 배율(--ui-zoom, 기본 한 단계 확대) 사용자 조절, 밝은 배경 대비 강화, 히어로 검색 배너 밝게, 추천 검색어 무테/무배경+호버 밑줄, 역할명(내부 직원/외부 사용자), 업무 분야 버튼 확대+중앙정렬, 사이드바 접기 토글 리디자인.
- 2026-08-24 — 폰트 배율 상한 확장(최대 260%), 다크 모드 글자 밝게+다크 전용 hover 색, 사이드바 접힘 hover 시 전체 펼침 제거(화살표만).
- 2026-08-24 — 홈 정리: 기능유형/많이찾는 섹션 제거, 즐겨찾는 프로그램 섹션 추가(가로 한 줄 박스 5개, 더보기→즐겨찾기 탭), 업무 분야 그리드 풀폭(7열)·아이콘 확대, 인기 순위 구분선 강조.
- 2026-08-24 — 프로그램 등록 가이드를 비전공자용 단계별 상세 문서로 재작성, 커스텀 스크롤바 적용, 모달 잘림 방지(헤더/푸터 고정 + 배율 보정 max-height).
- 2026-08-25 — 상세 화면 "웹에서 바로 사용": 배포된 서비스(deploymentOf)가 running이면 실제 URL로 여는 버튼 연결. repoName에서 .git 접미어 제거.
- 2026-08-24 — Phase 3: 등록 화면 "레포 규격 검증" 버튼(+결과 표시), 운영 관리자 "배포" 액션(파이프라인 로그/매니페스트 모달, DeployResultModal), api 클라이언트에 validate/deploy 추가. 타입체크/빌드 통과, 프록시 경유 POST 확인.
- 2026-08-25 — 인증 UI: pages/Auth 신설(로그인/회원가입/아이디 찾기/비밀번호 찾기 + 공통 AuthShell·검증 모듈). 아이디 형식·비밀번호 복잡도·이메일·필수값 클라이언트 검증, 이메일/휴대폰 인증번호 자리 배치. 데모 로그인은 별도 구획으로 분리해 유지. AppContext에 authView/account/signIn/세션 복구·자동 갱신 추가, api/auth.ts 클라이언트와 플랫폼 클라이언트 Bearer 헤더 주입, Vite 프록시에 /api/auth(→8089) 추가. 기존 pages/Login은 pages/Auth로 통합. 타입체크/빌드 통과. (npm typecheck 스크립트의 TS6310 오류 수정: tsc -b --noEmit → tsc -b)
- 2026-08-26 — 상향 권한 신청/승인 플로우: 회원가입은 항상 USER 생성, 가입 시 또는 마이페이지 "내 권한" 패널에서 coder/admin 신청(승인 전 취소 가능). 운영 관리자에 "권한 요청" 탭 추가(승인/반려). 탐색 목록을 역할 필터된 /api/programs로 로드(운영 관리자는 /programs/all). 문서 일괄 갱신(README/TEST/AGENT/PROCESS/가이드): API 모드 기본, 서브도메인 접속(`http://<slug>.localhost`), side marker 금지 반영.

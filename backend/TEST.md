# TEST.md · 백엔드 테스트 전략

## 자동 테스트

- `EduMsaApplicationTests` — 애플리케이션 컨텍스트 로드(H2, test 프로파일).
- `DeploymentServiceTransactionTest` — deploy() 트랜잭션 경계 검증: 장시간 외부 작업(clone 등)
  구간에 활성 트랜잭션이 없고, 상태(VALIDATING→…→RUNNING/FAILED)가 짧은 트랜잭션으로
  커밋되며, 성공 시 프로그램 공개가 배포 완료와 함께 커밋되는지 확인.
- `ProgramQueryTest` — 카탈로그 목록의 DB 단 필터/검색/정렬 의미 유지, 페이지네이션,
  N+1 제거(Hibernate statistics 로 페이지당 쿼리 수 고정 계측), 알림 미읽음 DB COUNT 검증.
- `DeploymentCleanupTest` — 배포 성공/실패/validate 모두 임시 clone 디렉터리가 finally 로
  정리되고, local:// 예제 경로(비-ephemeral)는 삭제되지 않는지 검증.
- `ServiceSpecSecurityTest` — P1-2 비신뢰 service.yaml 안전성: raw 치환 주입 재현(렌더러
  단독으론 annotation 주입 성립 → 검증이 거부해야 함을 계약으로 고정), 전역 !!태그·alias
  폭탄·과중첩·중복 키·멀티 문서·정의 외 필드 거부, name/health/cpu/memory 주입 문자 거부·
  정상값 통과, 정상 렌더 결과 5문서/kind/image/probe/requests 무결성 + 예상 외 필드 부재.
- `MaliciousSpecPipelineTest` — P1-2 파이프라인 차단: 악성 YAML 이 빌드/적용 전(파싱·검증)
  에서 FAILED(영구)로 끝나고 kubectl 이 한 번도 호출되지 않으며 작업 큐는 terminal(재시도 0).
- `NotificationOwnershipTest` — P1-1 알림 IDOR 차단: 실제 JWT 필터 체인(MockMvc)으로
  목록/unread-count 가 `?to=` 무시하고 principal 기준, 타인 알림 read 404(상태 불변·존재
  비노출), 본인 read 성공, read-all 은 본인만, 미인증 4종 401.
- `SlugClaimConcurrencyTest` — P0-2 slug TOCTOU 재현(동시 exists 검사 둘 다 통과) +
  원자 예약 검증: 8스레드 동시 claim 은 정확히 1승, 같은 프로그램 재예약 멱등,
  다른 프로그램 거부, validator 사전검사 연동.
- `DeployJobIdempotencyTest` — P0-2 중복 배포 흡수: 반복 enqueue(더블클릭·webhook replay)가
  기존 active 작업을 반환, RUNNING 중 흡수, 종료 후 신규 허용, completeTerminal 은
  재시도 없이 FAILED. (동시 INSERT 경쟁은 PostgreSQL 부분 유니크가 심판 — H2 미지원이라
  실 PostgreSQL/staging 에서 별도 실측)
- `DeploymentOwnershipCleanupTest` — P0-2 cleanup 소유권 보호: 다른 프로그램 소유
  (edu.msa/program-id 라벨) 리소스는 삭제하지 않고 경고, 본인 소유는 삭제+slug 예약 반납,
  라벨 없는 구버전 리소스는 기존대로 정리.
- `KanikoJobIsolationTest` — P0-1 빌드 격리 계약 고정: 빌드 ns 기본값 `edu-build`,
  렌더링된 Kaniko Job 의 전용 SA(edu-kaniko)/토큰 미마운트/명시적 securityContext
  (caps drop ALL·seccomp·no-priv-esc)/리소스 상한(cpu·mem·ephemeral-storage)/
  activeDeadline, 미치환 플레이스홀더 부재, repoUrl·branch 주입 방지(형식 위반 거부).
- `SchemaMigrationTest` — 빈 H2(PostgreSQL 모드)에 Flyway V1 실적용 →
  Hibernate `validate` 로 엔티티-스키마 일치 확인 → 시드 INSERT 까지 검증.
- `CatalogCacheTest` — 카탈로그 캐시(simple 캐시로 로직 검증): 같은 키 재조회는
  DB 쿼리 0, 변경 지점(evictor)이 캐시를 즉시 무효화해 최신을 반환.
- `DeployWorkerResilienceTest` — 재시도 지수 백오프(백오프 중 claim 제외→시각 경과 후 재선점),
  워커 강제 종료 시 방치 RUNNING 회수(작업 유실 없음), tick 1회에 큐 소진(drain) 검증.
- `ReadReplicaRoutingTest` — 서로 다른 H2 두 개(primary/replica)로 라우팅 증명:
  replica 마킹 조회(list/counts)는 replica(빈 DB → 0건), 비마킹 readOnly(all/detail)와
  쓰기는 primary(read-after-write 보존).
- Docker 빌드 시 `-x test`로 이미지 빌드를 빠르게 하고, 테스트는 별도로 수행 가능.
- 로컬(Windows) 주의: 사용자 경로에 한글이 있으면 Gradle 테스트 워커가 클래스패스를
  읽지 못한다. ASCII 정션 경로(`C:\edu-msa-build` → 본 저장소)에서
  `GRADLE_USER_HOME`을 ASCII 경로로 두고 `gradle build`를 실행하면 통과한다.

## 수동 검증 (compose 기동 후)

compose는 backend를 `:8088`에 노출한다. 대부분의 엔드포인트는 로그인(JWT)이 필요하므로
먼저 auth-service(`:8089`)에서 토큰을 받아 `Authorization: Bearer`로 실어 호출한다.

```bash
# 공개 엔드포인트 (토큰 불필요)
curl localhost:8088/api/health
curl localhost:8088/api/catalog

# 로그인 토큰 확보 (데모 로그인 예)
TOKEN=$(curl -s -X POST localhost:8089/api/auth/demo-login \
  -H 'Content-Type: application/json' -d '{"role":"admin"}' | jq -r .accessToken)

# 로그인 사용자 등급
curl -H "Authorization: Bearer $TOKEN" "localhost:8088/api/programs?sort=popular"
curl -H "Authorization: Bearer $TOKEN" localhost:8088/api/programs/1

# ADMIN 등급 — 검토/배포/사용자
curl -H "Authorization: Bearer $TOKEN" localhost:8088/api/programs/pending
curl -X POST localhost:8088/api/programs/1/review \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"action":"stop","memo":"확인"}'
curl -H "Authorization: Bearer $TOKEN" localhost:8088/api/users
```

## 체크리스트

- [ ] 시드 후 프로그램 7건(7개 내장 서비스), 사용자 7명.
- [ ] 목록 필터(cat/purpose/tech/scope/q)와 정렬(latest/popular/downloads) 동작.
- [ ] 상세에 readme/history/files/comments 포함.
- [ ] 인가(RBAC): 토큰 없이 보호 경로 호출 시 401, 등급 부족 시 403.
- [ ] `POST /api/programs`는 CODER 이상, 검토/권한/배포/사용자 관리는 ADMIN만 200.
- [ ] 승인/반려/중지/재개 시 상태 전이 + 이력 + 알림.
- [ ] 배포(작업 큐) 적재 후 워커가 처리, `/api/programs/{id}/deployment`에 상태 반영.
- [ ] CORS로 프론트(localhost:5173)에서 호출 가능(자격 증명 포함).

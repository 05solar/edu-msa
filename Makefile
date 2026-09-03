# edu-msa · 원커맨드 진입점 (deploy/bootstrap.sh 래퍼)
# 사용 예:
#   make up                      # 로컬 kind 에 코어+운영스택 전체 배포
#   make up WITH_GPU=1           # + NVIDIA GPU Operator
#   make core                    # 코어만(빠른 확인)
#   make up-server DOMAIN=edu.example.go.kr REGISTRY=registry.example:5000
#   make status / make down
SHELL := /bin/bash
BOOT  := ./deploy/bootstrap.sh

# 오버라이드 가능한 변수 (예: make up DOMAIN=... REGISTRY=... WITH_STACK=0)
MODE          ?= kind
DOMAIN        ?=
REGISTRY      ?=
IMAGE_TAG     ?= latest
WITH_STACK    ?= auto
WITH_GPU      ?= 0
WITH_EXAMPLES ?= 0
ENV = MODE=$(MODE) IMAGE_TAG=$(IMAGE_TAG) WITH_STACK=$(WITH_STACK) WITH_GPU=$(WITH_GPU) \
      WITH_EXAMPLES=$(WITH_EXAMPLES) \
      $(if $(DOMAIN),DOMAIN=$(DOMAIN),) $(if $(REGISTRY),REGISTRY=$(REGISTRY),)

.PHONY: up core images stack gpu examples status down up-server help
help:
	@echo "targets: up | core | images | stack | gpu | examples | status | down | up-server | prod-*"
	@echo "vars   : MODE=kind|server DOMAIN=.. REGISTRY=.. WITH_STACK=1|0 WITH_GPU=1|0 WITH_EXAMPLES=1|0"

up:         ; $(ENV) $(BOOT) up
core:       ; $(ENV) $(BOOT) core
images:     ; $(ENV) $(BOOT) images
stack:      ; $(ENV) $(BOOT) stack
gpu:        ; $(ENV) $(BOOT) gpu
examples:   ; $(ENV) $(BOOT) examples
status:     ; $(ENV) $(BOOT) status
down:       ; $(ENV) $(BOOT) down

# 실서버(기존 클러스터) 전체 배포 — DOMAIN·REGISTRY 필수
up-server:
	@[ -n "$(DOMAIN)" ]   || { echo "DOMAIN 을 지정하세요 (예: make up-server DOMAIN=edu.example.go.kr REGISTRY=...)"; exit 1; }
	@[ -n "$(REGISTRY)" ] || { echo "REGISTRY 를 지정하세요"; exit 1; }
	MODE=server DOMAIN=$(DOMAIN) REGISTRY=$(REGISTRY) IMAGE_TAG=$(IMAGE_TAG) \
	  WITH_STACK=$(WITH_STACK) WITH_GPU=$(WITH_GPU) $(BOOT) up

# ==== Production (edu-poc.headit.kr) ==========================================
# 운영 서버 전용 타겟. 도메인이 고정되어 있고, IMAGE_TAG 는 실행 시각으로 자동
# 부여되므로(예: 20260901-1830) 재배포 때마다 파드가 롤링 업데이트된다.
#
#   make prod-deploy   PROD_REGISTRY=<레지스트리>   # 최초/전체 (코어+운영스택)
#   make prod-core     PROD_REGISTRY=<레지스트리>   # 코드 반영 재배포 (평시 사용)
#   make prod-examples PROD_REGISTRY=<레지스트리>   # 기본 예제 7종 배포/갱신
#   make prod-status                                # 상태 확인
#
# 사전 조건: 서버 kubeconfig 로 클러스터 접근 가능, 레지스트리 push 권한,
#            DNS edu-poc.headit.kr (+ 예제 7종 쓰면 *.edu-poc.headit.kr) → Main Nginx → ingress.
PROD_DOMAIN   ?= edu-poc.headit.kr
PROD_REGISTRY ?=
PROD_TAG      ?= $(shell date +%Y%m%d-%H%M)
PROD_ENV = MODE=server DOMAIN=$(PROD_DOMAIN) REGISTRY=$(PROD_REGISTRY) IMAGE_TAG=$(PROD_TAG)

.PHONY: prod-guard prod-preflight prod-registry-secret prod-deploy prod-core prod-images prod-stack prod-examples prod-status
prod-guard:
	@[ -n "$(PROD_REGISTRY)" ] || { echo "PROD_REGISTRY 를 지정하세요 (예: make prod-core PROD_REGISTRY=registry.example:5000)"; exit 1; }
	@echo "▶ production 배포 대상: $(PROD_DOMAIN) · registry=$(PROD_REGISTRY) · tag=$(PROD_TAG)"

# 배포 전 사전 점검(읽기 전용) — 실서버 전제조건을 한 번에 확인한다.
# GitHub 레포 빌드(real 파이프라인)와 예제 7종 각각의 전제를 검사한다.
prod-preflight: prod-guard
	@echo "== 1. 클러스터 =="
	@kubectl config current-context
	@kubectl get nodes >/dev/null && echo "  ✓ 클러스터 접근 OK" || { echo "  ✗ kubeconfig 확인 필요"; exit 1; }
	@case "$$(kubectl config current-context)" in kind-*) echo "  ! 경고: kind 컨텍스트입니다 — 실서버 kubeconfig 가 맞는지 확인";; esac
	@echo "== 2. 레지스트리 (이미지 push/pull 경로) =="
	@docker info >/dev/null 2>&1 && echo "  ✓ docker 데몬 OK" || { echo "  ✗ docker 데몬 필요(이미지 빌드/푸시)"; exit 1; }
	@curl -fsk "https://$(PROD_REGISTRY)/v2/" >/dev/null 2>&1 || curl -fs "http://$(PROD_REGISTRY)/v2/" >/dev/null 2>&1 \
	  && echo "  ✓ 레지스트리 v2 응답 OK" || echo "  ! 레지스트리 $(PROD_REGISTRY) /v2/ 미응답 — 주소/인증 확인"
	@echo "== 3. GitHub 빌드(real 파이프라인) 전제 =="
	@kubectl -n edu-platform get sa edu-deployer >/dev/null 2>&1 && echo "  ✓ ServiceAccount edu-deployer" || echo "  ! rbac.yaml 미적용 (prod-core 가 적용함)"
	@kubectl auth can-i create jobs.batch -n edu-platform --as=system:serviceaccount:edu-platform:edu-deployer 2>/dev/null | grep -q yes \
	  && echo "  ✓ Kaniko Job 생성 권한" || echo "  ! Kaniko Job 권한 없음 — rbac.yaml 재적용 필요"
	@kubectl -n edu-platform get secret edu-registry-auth >/dev/null 2>&1 \
	  && echo "  ✓ 레지스트리 push Secret(edu-registry-auth)" \
	  || echo "  ! edu-registry-auth 없음 — 사설 레지스트리면 push 실패. make prod-registry-secret 으로 생성"
	@echo "== 4. 예제 7종(서브도메인) 전제 =="
	@getent hosts "$(PROD_DOMAIN)" >/dev/null 2>&1 && echo "  ✓ DNS $(PROD_DOMAIN)" || echo "  ! DNS $(PROD_DOMAIN) 미해석"
	@getent hosts "preflight-check.$(PROD_DOMAIN)" >/dev/null 2>&1 \
	  && echo "  ✓ 와일드카드 DNS *.$(PROD_DOMAIN)" \
	  || echo "  ! 와일드카드 *.$(PROD_DOMAIN) 미해석 — 예제 7종 접속 불가(핵심 기능은 무관)"
	@kubectl get ns cert-manager >/dev/null 2>&1 && echo "  ✓ cert-manager 설치됨" || echo "  ! cert-manager 없음 — prod-deploy(운영스택) 로 설치"
	@echo "== 점검 끝 — '!' 항목을 해결한 뒤 prod-deploy / prod-core 를 실행하세요 =="

# Kaniko 가 사설 레지스트리에 push 할 때 쓰는 인증 Secret 생성(멱등).
#   make prod-registry-secret PROD_REGISTRY=... REG_USER=... REG_PASS=...
prod-registry-secret: prod-guard
	@[ -n "$(REG_USER)" ] && [ -n "$(REG_PASS)" ] || { echo "REG_USER / REG_PASS 를 지정하세요"; exit 1; }
	kubectl -n edu-platform create secret docker-registry edu-registry-auth \
	  --docker-server=$(PROD_REGISTRY) --docker-username=$(REG_USER) --docker-password=$(REG_PASS) \
	  --dry-run=client -o yaml | kubectl apply -f -

prod-deploy:   prod-guard ; $(PROD_ENV) WITH_STACK=1 $(BOOT) up
prod-core:     prod-guard ; $(PROD_ENV) $(BOOT) core
prod-images:   prod-guard ; $(PROD_ENV) $(BOOT) images
prod-stack:    prod-guard ; $(PROD_ENV) $(BOOT) stack
prod-examples: prod-guard ; $(PROD_ENV) $(BOOT) examples
prod-status:   ; MODE=server DOMAIN=$(PROD_DOMAIN) $(BOOT) status

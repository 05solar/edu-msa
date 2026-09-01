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

.PHONY: prod-guard prod-deploy prod-core prod-images prod-stack prod-examples prod-status
prod-guard:
	@[ -n "$(PROD_REGISTRY)" ] || { echo "PROD_REGISTRY 를 지정하세요 (예: make prod-core PROD_REGISTRY=registry.example:5000)"; exit 1; }
	@echo "▶ production 배포 대상: $(PROD_DOMAIN) · registry=$(PROD_REGISTRY) · tag=$(PROD_TAG)"

prod-deploy:   prod-guard ; $(PROD_ENV) WITH_STACK=1 $(BOOT) up
prod-core:     prod-guard ; $(PROD_ENV) $(BOOT) core
prod-images:   prod-guard ; $(PROD_ENV) $(BOOT) images
prod-stack:    prod-guard ; $(PROD_ENV) $(BOOT) stack
prod-examples: prod-guard ; $(PROD_ENV) $(BOOT) examples
prod-status:   ; MODE=server DOMAIN=$(PROD_DOMAIN) $(BOOT) status

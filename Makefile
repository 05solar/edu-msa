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
	@echo "targets: up | core | images | stack | gpu | examples | status | down | up-server"
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

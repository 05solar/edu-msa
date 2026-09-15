-- V6 · Gitea 계정 발급 매핑 (MariaDB 동판 — H2 테스트용은 db/vendor/h2/V6)
--
-- 포털 사용자(auth 계정의 불변 uid)가 셀프 발급한 Gitea 계정의 매핑.
-- Gitea 계정 자체(비밀번호 포함)는 Gitea 가 소유하고, 여기는 "누가 어떤 아이디를
-- 발급받았는가"만 기록한다(재발급 차단·마이페이지 상태 표시용).
-- account_id/gitea_username 유니크가 동시 발급 경쟁의 최종 심판이다
-- (애플리케이션 사전 검사는 TOCTOU 라 UX 용).

create table gitea_accounts (
    id bigint auto_increment primary key,
    account_id bigint not null,
    gitea_username varchar(40) not null,
    created_at datetime(6) not null default current_timestamp(6),
    constraint uq_gitea_accounts_account unique (account_id),
    constraint uq_gitea_accounts_username unique (gitea_username)
);

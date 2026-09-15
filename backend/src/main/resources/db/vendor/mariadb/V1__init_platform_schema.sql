-- V1 · 플랫폼(backend) 초기 스키마 (MariaDB 동판 — H2 테스트용은 db/vendor/h2/V1)
-- Hibernate 가 엔티티에서 기대하는 DDL 을 기준으로 작성했다. 애플리케이션은 validate 로만 검증한다.
-- 기존 DB(이미 스키마 존재)는 flyway baseline-on-migrate(baseline-version=1)로 V1 을 건너뛴다.
--
-- MariaDB 방언 규칙(이 파일과 이후 모든 동판 공통):
--   identity                → bigint auto_increment (MariaDB 는 표준 identity 문법 미지원)
--   timestamp with time zone → datetime(6) — MariaDB 에 타임존 보존 타입이 없다.
--     TIMESTAMP 타입은 2038 한계 + 첫 컬럼 암묵 DEFAULT/ON UPDATE 부작용이 있어 쓰지 않는다.
--     시각은 앱(Instant/UTC)과 커넥터(timezone=UTC)가 UTC 로 고정해 저장한다.
--   문자셋: 서버 기본 utf8mb4 를 그대로 따른다(테이블별 charset 지정 없음).

create table app_users (
    id bigint auto_increment,
    dept varchar(255),
    name varchar(255) unique,
    role varchar(255) check (role in ('USER','CODER','ADMIN')),
    primary key (id)
);

create table programs (
    id bigint auto_increment,
    created_at date,
    updated_at date,
    views integer not null,
    likes integer not null,
    downloads integer not null,
    app_version varchar(255),
    branch varchar(255),
    cat varchar(255),
    dept varchar(255),
    description text,
    name varchar(255),
    owner varchar(255),
    reject_reason text,
    repo_url varchar(255),
    scope varchar(255) check (scope in ('ALL','DEPT')),
    slug varchar(255) unique,
    status varchar(255) check (status in ('DRAFT','PENDING','PUBLIC','REJECTED','STOPPED')),
    stop_reason text,
    summary text,
    primary key (id)
);

create table program_tags (
    program_id bigint not null,
    tag varchar(255),
    constraint fk_program_tags_program foreign key (program_id) references programs (id)
);

create table program_tech (
    program_id bigint not null,
    tech varchar(255),
    constraint fk_program_tech_program foreign key (program_id) references programs (id)
);

create table program_purposes (
    program_id bigint not null,
    purpose varchar(255),
    constraint fk_program_purposes_program foreign key (program_id) references programs (id)
);

create table program_run (
    program_id bigint not null,
    run_type varchar(255),
    constraint fk_program_run_program foreign key (program_id) references programs (id)
);

create table program_features (
    program_id bigint not null,
    idx integer not null,
    feature text,
    primary key (idx, program_id),
    constraint fk_program_features_program foreign key (program_id) references programs (id)
);

create table program_readme (
    program_id bigint not null,
    idx integer not null,
    line text,
    primary key (idx, program_id),
    constraint fk_program_readme_program foreign key (program_id) references programs (id)
);

create table program_history (
    program_id bigint not null,
    idx integer not null,
    date varchar(255),
    log text,
    ver varchar(255),
    primary key (idx, program_id),
    constraint fk_program_history_program foreign key (program_id) references programs (id)
);

create table program_files (
    program_id bigint not null,
    idx integer not null,
    file_name varchar(255),
    file_size varchar(255),
    file_type varchar(255),
    primary key (idx, program_id),
    constraint fk_program_files_program foreign key (program_id) references programs (id)
);

create table comments (
    id bigint auto_increment,
    program_id bigint,
    body text,
    comment_time varchar(255),
    comment_user varchar(255),
    dept varchar(255),
    reply_body text,
    reply_dept varchar(255),
    reply_time varchar(255),
    reply_user varchar(255),
    primary key (id)
);

create table notifications (
    id bigint auto_increment,
    is_read boolean,
    program_id bigint,
    kind varchar(255) check (kind in ('COMMENT','REJECT','SUBMIT','VERSION','APPROVE')),
    sub text,
    title text,
    to_user varchar(255),
    primary key (id)
);

create table deployments (
    id bigint auto_increment,
    program_id bigint,
    host_port integer,
    created_at datetime(6),
    updated_at datetime(6),
    branch varchar(255),
    image_tag varchar(255),
    log_text text,
    manifest text,
    name varchar(255),
    repo_url varchar(255),
    slug varchar(255),
    status varchar(255) check (status in ('PENDING','VALIDATING','BUILDING','DEPLOYING','RUNNING','FAILED')),
    url varchar(255),
    primary key (id)
);

create table deploy_jobs (
    id bigint auto_increment,
    program_id bigint,
    deployment_id bigint,
    attempts integer not null,
    max_attempts integer not null,
    created_at datetime(6),
    updated_at datetime(6),
    actor varchar(255),
    branch varchar(255),
    last_error text,
    repo_url text,
    status varchar(255) check (status in ('QUEUED','RUNNING','DONE','FAILED')),
    primary key (id)
);

create table review_logs (
    id bigint auto_increment,
    program_id bigint,
    action varchar(255) check (action in ('APPROVE','REJECT','STOP','RESUME')),
    logged_at varchar(255),
    memo text,
    reviewer varchar(255),
    title varchar(255),
    primary key (id)
);

-- 조회 인덱스 (엔티티 @Index 선언과 동일)
create index idx_programs_status_updated on programs (status, updated_at);
create index idx_programs_status_views on programs (status, views);
create index idx_programs_status_downloads on programs (status, downloads);
create index idx_programs_status_cat on programs (status, cat);
create index idx_programs_owner on programs (owner);
create index idx_program_tags_pid_tag on program_tags (program_id, tag);
create index idx_program_tech_pid_tech on program_tech (program_id, tech);
create index idx_program_purposes_pid_purpose on program_purposes (program_id, purpose);
create index idx_comments_program on comments (program_id);
create index idx_notifications_to_read on notifications (to_user, is_read);
create index idx_deployments_program on deployments (program_id);

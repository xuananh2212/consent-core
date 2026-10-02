-- Tài khoản admin của trang quản lý consent.
-- Ứng dụng cũng tự tạo hai bảng này lúc khởi động nếu chưa có.

create table admin_user (
    id             varchar2(36) primary key,
    username       varchar2(100) not null,
    password_hash  varchar2(200) not null,
    display_name   varchar2(200) not null,
    tenant_id      varchar2(100) not null,
    actor_id       varchar2(200) not null,
    actor_type     varchar2(30) not null,
    enabled        number(1) default 1 not null,
    created_at     timestamp with time zone not null,
    constraint uk_admin_user_username unique (username)
);

create table admin_refresh_token (
    id          varchar2(36) primary key,
    user_id     varchar2(36) not null,
    token_hash  varchar2(64) not null,
    expires_at  timestamp with time zone not null,
    revoked_at  timestamp with time zone,
    created_at  timestamp with time zone not null,
    constraint uk_admin_refresh_hash unique (token_hash),
    constraint fk_admin_refresh_user foreign key (user_id) references admin_user (id)
);

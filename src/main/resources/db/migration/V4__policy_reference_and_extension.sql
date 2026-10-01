CREATE TABLE consent_type_policy
(
    id                               RAW(16) PRIMARY KEY,
    tenant_id                        VARCHAR2(100) NOT NULL,
    consent_type                     VARCHAR2(100) NOT NULL,
    evidence_policy                  VARCHAR2(40) NOT NULL,
    max_validity_days                NUMBER(10) NOT NULL,
    require_subject                  NUMBER(1) DEFAULT 1 NOT NULL,
    allow_trusted_auto_authorization NUMBER(1) DEFAULT 0 NOT NULL,
    allowed_channels                 CLOB DEFAULT '[]'        NOT NULL,
    active                           NUMBER(1) DEFAULT 1 NOT NULL,
    created_at                       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by                       VARCHAR2(200) NOT NULL,
    updated_at                       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by                       VARCHAR2(200) NOT NULL,
    version                          NUMBER(19) DEFAULT 0 NOT NULL,
    CONSTRAINT uk_consent_type_policy UNIQUE (tenant_id,
                                              consent_type),
    CONSTRAINT ck_policy_evidence_policy CHECK ( evidence_policy IN ('REQUIRED', 'NOT_REQUIRED') ),
    CONSTRAINT ck_policy_max_validity CHECK ( max_validity_days > 0 ),
    CONSTRAINT ck_policy_require_subj CHECK ( require_subject IN (0, 1) ),
    CONSTRAINT ck_policy_allow_trusted CHECK ( allow_trusted_auto_authorization IN (0, 1) ),
    CONSTRAINT ck_policy_active CHECK ( active IN (0, 1) ),
    CONSTRAINT ck_policy_channels_json CHECK ( allowed_channels IS JSON )
);

CREATE TABLE trusted_consent_source
(
    id                    RAW(16) PRIMARY KEY,
    tenant_id             VARCHAR2(100) NOT NULL,
    source_system         VARCHAR2(100) NOT NULL,
    client_id             VARCHAR2(200),
    allowed_consent_types CLOB DEFAULT '[]'        NOT NULL,
    active                NUMBER(1) DEFAULT 1 NOT NULL,
    valid_from            TIMESTAMP WITH TIME ZONE,
    valid_until           TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by            VARCHAR2(200) NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by            VARCHAR2(200) NOT NULL,
    CONSTRAINT uk_trusted_consent_source UNIQUE (tenant_id,
                                                 source_system,
                                                 client_id),
    CONSTRAINT ck_trusted_source_active CHECK ( active IN (0, 1) ),
    CONSTRAINT ck_trusted_source_validity CHECK ( valid_until IS NULL
        OR valid_from IS NULL
        OR valid_until > valid_from ),
    CONSTRAINT ck_trusted_types_json CHECK ( allowed_consent_types IS JSON )
);

CREATE TABLE policy_evaluation
(
    id                RAW(16) PRIMARY KEY,
    tenant_id         VARCHAR2(100) NOT NULL,
    consent_id        RAW(16),
    operation         VARCHAR2(80) NOT NULL,
    policy_id         RAW(16),
    result            VARCHAR2(30) NOT NULL,
    reasons           CLOB DEFAULT '[]'        NOT NULL,
    evaluated_context CLOB DEFAULT '{}'        NOT NULL,
    evaluated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_policy_eval_consent FOREIGN KEY (consent_id)
        REFERENCES consent (id),
    CONSTRAINT fk_policy_eval_policy FOREIGN KEY (policy_id)
        REFERENCES consent_type_policy (id),
    CONSTRAINT ck_policy_eval_result CHECK ( result IN ('ALLOW', 'DENY') ),
    CONSTRAINT ck_policy_eval_reasons_json CHECK ( reasons IS JSON ),
    CONSTRAINT ck_policy_eval_ctx_json CHECK ( evaluated_context IS JSON )
);

CREATE INDEX ix_policy_eval_consent ON
    policy_evaluation (
                       tenant_id,
                       consent_id,
                       evaluated_at
                       DESC);

CREATE TABLE extension_registration
(
    id                  RAW(16) PRIMARY KEY,
    extension_name      VARCHAR2(150) NOT NULL UNIQUE,
    description         VARCHAR2(500),
    implementation_type VARCHAR2(50) NOT NULL,
    active              NUMBER(1) DEFAULT 1 NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(200) NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR2(200) NOT NULL,
    CONSTRAINT ck_ext_impl_type CHECK ( implementation_type IN ('SPRING_BEAN') ),
    CONSTRAINT ck_ext_reg_active CHECK ( active IN (0, 1) )
);

CREATE TABLE extension_binding
(
    id                  RAW(16) PRIMARY KEY,
    tenant_id           VARCHAR2(100) NOT NULL,
    extension_name      VARCHAR2(150) NOT NULL,
    extension_point     VARCHAR2(100) NOT NULL,
    consent_type        VARCHAR2(100),
    acquisition_channel VARCHAR2(60),
    evidence_type       VARCHAR2(80),
    execution_order     NUMBER(10) DEFAULT 100 NOT NULL,
    critical            NUMBER(1) DEFAULT 0 NOT NULL,
    timeout_ms          NUMBER(10) DEFAULT 2000 NOT NULL,
    max_attempts        NUMBER(10) DEFAULT 1 NOT NULL,
    configuration       CLOB DEFAULT '{}'        NOT NULL,
    active              NUMBER(1) DEFAULT 1 NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR2(200) NOT NULL,
    CONSTRAINT fk_ext_bind_reg FOREIGN KEY (extension_name)
        REFERENCES extension_registration (extension_name),
    CONSTRAINT ck_ext_timeout CHECK ( timeout_ms > 0 ),
    CONSTRAINT ck_ext_attempts CHECK ( max_attempts > 0 ),
    CONSTRAINT ck_ext_critical CHECK ( critical IN (0, 1) ),
    CONSTRAINT ck_ext_active CHECK ( active IN (0, 1) ),
    CONSTRAINT ck_ext_config_json CHECK ( configuration IS JSON )
);

CREATE INDEX ix_ext_bind_lookup ON
    extension_binding (
                       tenant_id,
                       extension_point,
                       active,
                       execution_order
        );

CREATE TABLE extension_execution_log
(
    id              RAW(16) PRIMARY KEY,
    tenant_id       VARCHAR2(100) NOT NULL,
    binding_id      RAW(16) NOT NULL,
    consent_id      RAW(16),
    extension_point VARCHAR2(100) NOT NULL,
    attempt         NUMBER(10) NOT NULL,
    result          VARCHAR2(30) NOT NULL,
    duration_ms     NUMBER(19) NOT NULL,
    error_message   VARCHAR2(2000),
    correlation_id  VARCHAR2(100) NOT NULL,
    executed_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ext_exec_bind FOREIGN KEY (binding_id)
        REFERENCES extension_binding (id),
    CONSTRAINT fk_ext_exec_consent FOREIGN KEY (consent_id)
        REFERENCES consent (id),
    CONSTRAINT ck_ext_exec_result CHECK ( result IN ('SUCCESS', 'FAILURE', 'TIMEOUT', 'SKIPPED') )
);

CREATE INDEX ix_ext_exec_consent ON
    extension_execution_log (
                             tenant_id,
                             consent_id,
                             executed_at
                             DESC);

CREATE TABLE permission_catalog
(
    id                RAW(16) PRIMARY KEY,
    tenant_id         VARCHAR2(100) NOT NULL,
    permission_code   VARCHAR2(150) NOT NULL,
    display_name      VARCHAR2(300) NOT NULL,
    description       VARCHAR2(1000),
    schema_version    NUMBER(10) DEFAULT 1 NOT NULL,
    attributes_schema CLOB DEFAULT '{}' NOT NULL,
    active            NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT uk_permission_catalog UNIQUE (tenant_id,
                                             permission_code),
    CONSTRAINT ck_perm_cat_active CHECK ( active IN (0, 1) ),
    CONSTRAINT ck_perm_cat_attr_json CHECK ( attributes_schema IS JSON )
);
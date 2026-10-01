-- Generic, configuration-driven Consent Context & Data Enrichment framework.
-- Business mapping lives in PostgreSQL/reference configuration; application.yml remains technical bootstrap only.

CREATE TABLE consent_data_requirement (
                                          id                RAW(16) PRIMARY KEY,
                                          tenant_id         VARCHAR2(100) NOT NULL,
                                          consent_type_code VARCHAR2(100) NOT NULL,
                                          requirement_code  VARCHAR2(120) NOT NULL,
                                          resolution_phase  VARCHAR2(60) NOT NULL,
                                          data_type         VARCHAR2(120) NOT NULL,
                                          data_purpose      VARCHAR2(40) NOT NULL,
                                          required          NUMBER(1) DEFAULT 1 NOT NULL,
                                          schema_version    NUMBER(10) DEFAULT 1 NOT NULL,
                                          freshness_seconds NUMBER(10),
                                          configuration     CLOB DEFAULT '{}' NOT NULL,
                                          active            NUMBER(1) DEFAULT 1 NOT NULL,
                                          created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                          created_by        VARCHAR2(200) NOT NULL,
                                          updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                          updated_by        VARCHAR2(200) NOT NULL,
                                          version           NUMBER(19) DEFAULT 0 NOT NULL,
                                          CONSTRAINT fk_data_req_consent_type FOREIGN KEY ( tenant_id,
                                                                                            consent_type_code )
                                              REFERENCES consent_type_catalog ( tenant_id,
                                                                                consent_type_code ),
                                          CONSTRAINT uk_data_requirement UNIQUE ( tenant_id,
                                                                                  consent_type_code,
                                                                                  resolution_phase,
                                                                                  requirement_code ),
                                          CONSTRAINT ck_data_req_phase CHECK ( resolution_phase IN ( 'REGISTRATION', 'PREPARE_AUTHORIZATION', 'AUTHORIZATION_VALIDATION' ) )
    ,
                                          CONSTRAINT ck_data_req_purpose CHECK ( data_purpose IN ( 'SELECTION', 'CONTEXT', 'POLICY' ) ),
                                          CONSTRAINT ck_data_req_schema_ver CHECK ( schema_version > 0 ),
                                          CONSTRAINT ck_data_req_freshness CHECK ( freshness_seconds IS NULL
                                              OR freshness_seconds > 0 ),
                                          CONSTRAINT ck_data_req_required CHECK ( required IN ( 0, 1 ) ),
                                          CONSTRAINT ck_data_req_active CHECK ( active IN ( 0, 1 ) ),
                                          CONSTRAINT ck_data_req_cfg_json CHECK ( configuration IS JSON )
);

CREATE INDEX ix_data_req_lookup ON
    consent_data_requirement (
                              tenant_id,
                              consent_type_code,
                              resolution_phase,
                              active,
                              requirement_code
        );

CREATE TABLE data_provider_definition (
                                          id                   RAW(16) PRIMARY KEY,
                                          tenant_id            VARCHAR2(100) NOT NULL,
                                          provider_code        VARCHAR2(120) NOT NULL,
                                          provider_type        VARCHAR2(40) NOT NULL,
                                          adapter_key          VARCHAR2(120) NOT NULL,
                                          endpoint_template    VARCHAR2(1000),
                                          http_method          VARCHAR2(10),
                                          credential_reference VARCHAR2(500),
                                          timeout_ms           NUMBER(10) DEFAULT 3000 NOT NULL,
                                          configuration        CLOB DEFAULT '{}' NOT NULL,
                                          active               NUMBER(1) DEFAULT 1 NOT NULL,
                                          created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
                                          created_by           VARCHAR2(200) NOT NULL,
                                          updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
                                          updated_by           VARCHAR2(200) NOT NULL,
                                          version              NUMBER(19) DEFAULT 0 NOT NULL,
                                          CONSTRAINT uk_data_provider_definition UNIQUE ( tenant_id,
                                                                                          provider_code ),
                                          CONSTRAINT ck_data_provider_type CHECK ( provider_type IN ( 'MOCK', 'REST', 'SOAP', 'DATABASE', 'MESSAGE',
                                                                                                      'COMPOSITE', 'CUSTOM' ) ),
                                          CONSTRAINT ck_data_provider_http_method CHECK ( http_method IS NULL
                                              OR http_method IN ( 'GET', 'POST' ) ),
                                          CONSTRAINT ck_data_provider_timeout CHECK ( timeout_ms BETWEEN 100 AND 120000 ),
                                          CONSTRAINT ck_data_provider_active CHECK ( active IN ( 0, 1 ) ),
                                          CONSTRAINT ck_data_provider_cfg_json CHECK ( configuration IS JSON )
);

CREATE TABLE data_mapping_profile (
                                      id                   RAW(16) PRIMARY KEY,
                                      tenant_id            VARCHAR2(100) NOT NULL,
                                      mapping_profile_code VARCHAR2(120) NOT NULL,
                                      data_type            VARCHAR2(120) NOT NULL,
                                      provider_code        VARCHAR2(120) NOT NULL,
                                      schema_version       NUMBER(10) DEFAULT 1 NOT NULL,
                                      configuration        CLOB DEFAULT '{}' NOT NULL,
                                      active               NUMBER(1) DEFAULT 1 NOT NULL,
                                      created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
                                      created_by           VARCHAR2(200) NOT NULL,
                                      updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
                                      updated_by           VARCHAR2(200) NOT NULL,
                                      version              NUMBER(19) DEFAULT 0 NOT NULL,
                                      CONSTRAINT fk_mapping_provider FOREIGN KEY ( tenant_id,
                                                                                   provider_code )
                                          REFERENCES data_provider_definition ( tenant_id,
                                                                                provider_code ),
                                      CONSTRAINT uk_data_mapping_profile UNIQUE ( tenant_id,
                                                                                  mapping_profile_code ),
                                      CONSTRAINT ck_data_mapping_schema_ver CHECK ( schema_version > 0 ),
                                      CONSTRAINT ck_data_mapping_active CHECK ( active IN ( 0, 1 ) ),
                                      CONSTRAINT ck_data_mapping_cfg_json CHECK ( configuration IS JSON )
);

CREATE TABLE data_provider_binding (
                                       id                   RAW(16) PRIMARY KEY,
                                       tenant_id            VARCHAR2(100) NOT NULL,
                                       data_type            VARCHAR2(120) NOT NULL,
                                       consent_type_code    VARCHAR2(100),
                                       provider_code        VARCHAR2(120) NOT NULL,
                                       mapping_profile_code VARCHAR2(120),
                                       priority             NUMBER(10) DEFAULT 100 NOT NULL,
                                       configuration        CLOB DEFAULT '{}' NOT NULL,
                                       active               NUMBER(1) DEFAULT 1 NOT NULL,
                                       created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
                                       created_by           VARCHAR2(200) NOT NULL,
                                       updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
                                       updated_by           VARCHAR2(200) NOT NULL,
                                       version              NUMBER(19) DEFAULT 0 NOT NULL,
                                       CONSTRAINT fk_provider_binding_provider FOREIGN KEY ( tenant_id,
                                                                                             provider_code )
                                           REFERENCES data_provider_definition ( tenant_id,
                                                                                 provider_code ),
                                       CONSTRAINT fk_provider_binding_mapping FOREIGN KEY ( tenant_id,
                                                                                            mapping_profile_code )
                                           REFERENCES data_mapping_profile ( tenant_id,
                                                                             mapping_profile_code ),
                                       CONSTRAINT fk_provider_binding_type FOREIGN KEY ( tenant_id,
                                                                                         consent_type_code )
                                           REFERENCES consent_type_catalog ( tenant_id,
                                                                             consent_type_code ),
                                       CONSTRAINT ck_provider_binding_priority CHECK ( priority >= 0 ),
                                       CONSTRAINT ck_provider_binding_active CHECK ( active IN ( 0, 1 ) ),
                                       CONSTRAINT ck_provider_binding_cfg_json CHECK ( configuration IS JSON )
);

CREATE UNIQUE INDEX uk_provider_binding_scope ON
    data_provider_binding (
                           tenant_id,
                           data_type,
                           nvl(
        consent_type_code, ' '),
                           provider_code
        );

CREATE INDEX ix_provider_binding_lookup ON
    data_provider_binding (
                           tenant_id,
                           data_type,
                           consent_type_code,
                           active,
                           priority,
                           provider_code
        );

CREATE TABLE consent_data_resolution_log (
                                             id                   RAW(16) PRIMARY KEY,
                                             tenant_id            VARCHAR2(100) NOT NULL,
                                             consent_id           RAW(16) NOT NULL,
                                             consent_type_code    VARCHAR2(100) NOT NULL,
                                             requirement_code     VARCHAR2(120) NOT NULL,
                                             data_type            VARCHAR2(120) NOT NULL,
                                             data_purpose         VARCHAR2(40) NOT NULL,
                                             resolution_phase     VARCHAR2(60) NOT NULL,
                                             provider_code        VARCHAR2(120),
                                             result_status        VARCHAR2(30) NOT NULL,
                                             normalized_data_hash VARCHAR2(128),
                                             candidate_set_hash   VARCHAR2(128),
                                             duration_ms          NUMBER(19),
                                             error_code           VARCHAR2(120),
                                             correlation_id       VARCHAR2(200),
                                             resolved_at          TIMESTAMP WITH TIME ZONE NOT NULL,
                                             expires_at           TIMESTAMP WITH TIME ZONE,
                                             CONSTRAINT fk_data_res_consent FOREIGN KEY ( consent_id )
                                                 REFERENCES consent ( id ),
                                             CONSTRAINT ck_data_res_result CHECK ( result_status IN ( 'SUCCESS', 'OPTIONAL_FAILURE', 'FAILED' ) ),
                                             CONSTRAINT ck_data_res_duration CHECK ( duration_ms IS NULL
                                                 OR duration_ms >= 0 )
);

CREATE INDEX ix_data_res_consent ON
    consent_data_resolution_log (
                                 tenant_id,
                                 consent_id,
                                 resolved_at
                                 DESC );

CREATE INDEX ix_data_res_req ON
    consent_data_resolution_log (
                                 tenant_id,
                                 consent_type_code,
                                 requirement_code,
                                 resolved_at
                                 DESC );

COMMENT ON TABLE consent_data_requirement IS
    'Tenant/consent-type configuration declaring which logical backend data must be resolved at each consent phase.';

COMMENT ON TABLE data_provider_definition IS
    'Deployment configuration for replaceable backend providers; secrets are references only, never plaintext credentials.';

COMMENT ON TABLE data_provider_binding IS
    'Maps a logical data_type to a provider, optionally overridden for a consent type.';

COMMENT ON TABLE data_mapping_profile IS
    'Versioned normalization/mapping configuration from provider payload to canonical enrichment data.';

COMMENT ON TABLE consent_data_resolution_log IS
    'Data-minimized execution provenance. Raw backend payload is intentionally not persisted.';
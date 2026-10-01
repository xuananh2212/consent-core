-- Completes the high-level Consent Core scope after the executable Registry baseline.
CREATE TABLE consent_obligation (
                                    id              RAW(16) PRIMARY KEY,
                                    tenant_id       VARCHAR2(100) NOT NULL,
                                    revision_id     RAW(16) NOT NULL,
                                    obligation_type VARCHAR2(120) NOT NULL,
                                    parameters      CLOB DEFAULT '{}' NOT NULL,
                                    sort_order      NUMBER(10) DEFAULT 0 NOT NULL,
                                    CONSTRAINT fk_consent_oblig_rev FOREIGN KEY ( revision_id )
                                        REFERENCES consent_revision ( id ),
                                    CONSTRAINT uk_consent_obligation UNIQUE ( revision_id,
                                                                              obligation_type,
                                                                              sort_order ),
                                    CONSTRAINT ck_oblig_params_json CHECK ( parameters IS JSON )
);

CREATE INDEX ix_consent_oblig_rev ON
    consent_obligation (
                        tenant_id,
                        revision_id,
                        sort_order
        );

CREATE OR REPLACE TRIGGER trg_oblig_rev_guard BEFORE
    INSERT OR UPDATE OR DELETE ON consent_obligation
    FOR EACH ROW
DECLARE
v_target_revision RAW(16);
    v_revision_status VARCHAR2(30);
BEGIN
    IF deleting THEN
        v_target_revision := :old.revision_id;
ELSE
        v_target_revision := :new.revision_id;
END IF;

SELECT
    status
INTO v_revision_status
FROM
    consent_revision
WHERE
    id = v_target_revision;

IF v_revision_status <> 'DRAFT' THEN
        raise_application_error(-20014, 'Content of finalized consent revision is immutable');
END IF;
EXCEPTION
    WHEN no_data_found THEN
        NULL;
END;
/

CREATE TABLE consent_type_catalog (
                                      id                RAW(16) PRIMARY KEY,
                                      tenant_id         VARCHAR2(100) NOT NULL,
                                      consent_type_code VARCHAR2(100) NOT NULL,
                                      display_name      VARCHAR2(300) NOT NULL,
                                      description       VARCHAR2(1000),
                                      schema_version    NUMBER(10) DEFAULT 1 NOT NULL,
                                      content_schema    CLOB DEFAULT '{}' NOT NULL,
                                      active            NUMBER(1) DEFAULT 1 NOT NULL,
                                      created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                      created_by        VARCHAR2(200) NOT NULL,
                                      updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                      updated_by        VARCHAR2(200) NOT NULL,
                                      version           NUMBER(19) DEFAULT 0 NOT NULL,
                                      CONSTRAINT uk_consent_type_catalog UNIQUE ( tenant_id,
                                                                                  consent_type_code ),
                                      CONSTRAINT ck_consent_type_schema_ver CHECK ( schema_version > 0 ),
                                      CONSTRAINT ck_consent_type_cat_active CHECK ( active IN ( 0, 1 ) ),
                                      CONSTRAINT ck_consent_type_schema_json CHECK ( content_schema IS JSON )
);

CREATE TABLE purpose_definition (
                                    id                RAW(16) PRIMARY KEY,
                                    tenant_id         VARCHAR2(100) NOT NULL,
                                    purpose_code      VARCHAR2(100) NOT NULL,
                                    display_name      VARCHAR2(300) NOT NULL,
                                    description       VARCHAR2(1000),
                                    legal_basis       VARCHAR2(200),
                                    schema_version    NUMBER(10) DEFAULT 1 NOT NULL,
                                    attributes_schema CLOB DEFAULT '{}' NOT NULL,
                                    active            NUMBER(1) DEFAULT 1 NOT NULL,
                                    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                    created_by        VARCHAR2(200) NOT NULL,
                                    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                    updated_by        VARCHAR2(200) NOT NULL,
                                    version           NUMBER(19) DEFAULT 0 NOT NULL,
                                    CONSTRAINT uk_purpose_definition UNIQUE ( tenant_id,
                                                                              purpose_code ),
                                    CONSTRAINT ck_purpose_schema_version CHECK ( schema_version > 0 ),
                                    CONSTRAINT ck_purpose_def_active CHECK ( active IN ( 0, 1 ) ),
                                    CONSTRAINT ck_purpose_schema_json CHECK ( attributes_schema IS JSON )
);

CREATE TABLE capture_method_catalog (
                                        id                    RAW(16) PRIMARY KEY,
                                        tenant_id             VARCHAR2(100) NOT NULL,
                                        capture_method_code   VARCHAR2(100) NOT NULL,
                                        display_name          VARCHAR2(300) NOT NULL,
                                        description           VARCHAR2(1000),
                                        default_evidence_type VARCHAR2(100),
                                        configuration         CLOB DEFAULT '{}' NOT NULL,
                                        active                NUMBER(1) DEFAULT 1 NOT NULL,
                                        created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
                                        created_by            VARCHAR2(200) NOT NULL,
                                        updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
                                        updated_by            VARCHAR2(200) NOT NULL,
                                        version               NUMBER(19) DEFAULT 0 NOT NULL,
                                        CONSTRAINT uk_capture_method_catalog UNIQUE ( tenant_id,
                                                                                      capture_method_code ),
                                        CONSTRAINT ck_capture_method_active CHECK ( active IN ( 0, 1 ) ),
                                        CONSTRAINT ck_capture_method_cfg_json CHECK ( configuration IS JSON )
);

CREATE TABLE storage_profile (
                                 id                       RAW(16) PRIMARY KEY,
                                 tenant_id                VARCHAR2(100) NOT NULL,
                                 profile_code             VARCHAR2(100) NOT NULL,
                                 provider_type            VARCHAR2(60) NOT NULL,
                                 encryption_key_reference VARCHAR2(300),
                                 configuration            CLOB DEFAULT '{}' NOT NULL,
                                 active                   NUMBER(1) DEFAULT 1 NOT NULL,
                                 created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
                                 created_by               VARCHAR2(200) NOT NULL,
                                 updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
                                 updated_by               VARCHAR2(200) NOT NULL,
                                 version                  NUMBER(19) DEFAULT 0 NOT NULL,
                                 CONSTRAINT uk_storage_profile UNIQUE ( tenant_id,
                                                                        profile_code ),
                                 CONSTRAINT ck_storage_provider_type CHECK ( provider_type IN ( 'LOCAL', 'S3_COMPATIBLE', 'DMS', 'ECM', 'CUSTOM' ) ),
                                 CONSTRAINT ck_storage_prof_active CHECK ( active IN ( 0, 1 ) ),
                                 CONSTRAINT ck_storage_prof_cfg_json CHECK ( configuration IS JSON )
);

CREATE TABLE retention_profile (
                                   id                      RAW(16) PRIMARY KEY,
                                   tenant_id               VARCHAR2(100) NOT NULL,
                                   profile_code            VARCHAR2(100) NOT NULL,
                                   consent_retention_days  NUMBER(10) NOT NULL,
                                   evidence_retention_days NUMBER(10) NOT NULL,
                                   legal_hold_supported    NUMBER(1) DEFAULT 1 NOT NULL,
                                   purge_strategy          VARCHAR2(40) DEFAULT 'HARD_DELETE_ARTIFACT' NOT NULL,
                                   configuration           CLOB DEFAULT '{}' NOT NULL,
                                   active                  NUMBER(1) DEFAULT 1 NOT NULL,
                                   created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
                                   created_by              VARCHAR2(200) NOT NULL,
                                   updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
                                   updated_by              VARCHAR2(200) NOT NULL,
                                   version                 NUMBER(19) DEFAULT 0 NOT NULL,
                                   CONSTRAINT uk_retention_profile UNIQUE ( tenant_id,
                                                                            profile_code ),
                                   CONSTRAINT ck_retention_days CHECK ( consent_retention_days > 0
                                       AND evidence_retention_days > 0 ),
                                   CONSTRAINT ck_purge_strategy CHECK ( purge_strategy IN ( 'HARD_DELETE_ARTIFACT', 'CRYPTO_SHRED', 'ARCHIVE_THEN_DELETE', 'CUSTOM' )
                                       ),
                                   CONSTRAINT ck_ret_prof_hold CHECK ( legal_hold_supported IN ( 0, 1 ) ),
                                   CONSTRAINT ck_ret_prof_active CHECK ( active IN ( 0, 1 ) ),
                                   CONSTRAINT ck_ret_prof_cfg_json CHECK ( configuration IS JSON )
);

CREATE TABLE tenant_configuration (
                                      tenant_id                      VARCHAR2(100) PRIMARY KEY,
                                      default_locale                 VARCHAR2(30) DEFAULT 'en' NOT NULL,
                                      default_storage_profile_code   VARCHAR2(100),
                                      default_retention_profile_code VARCHAR2(100),
                                      schema_version                 NUMBER(10) DEFAULT 1 NOT NULL,
                                      configuration                  CLOB DEFAULT '{}' NOT NULL,
                                      created_at                     TIMESTAMP WITH TIME ZONE NOT NULL,
                                      created_by                     VARCHAR2(200) NOT NULL,
                                      updated_at                     TIMESTAMP WITH TIME ZONE NOT NULL,
                                      updated_by                     VARCHAR2(200) NOT NULL,
                                      version                        NUMBER(19) DEFAULT 0 NOT NULL,
                                      CONSTRAINT ck_tenant_cfg_schema_ver CHECK ( schema_version > 0 ),
                                      CONSTRAINT fk_tenant_storage_profile FOREIGN KEY ( tenant_id,
                                                                                         default_storage_profile_code )
                                          REFERENCES storage_profile ( tenant_id,
                                                                       profile_code ),
                                      CONSTRAINT fk_tenant_retention_profile FOREIGN KEY ( tenant_id,
                                                                                           default_retention_profile_code )
                                          REFERENCES retention_profile ( tenant_id,
                                                                         profile_code ),
                                      CONSTRAINT ck_tenant_cfg_json CHECK ( configuration IS JSON )
);

CREATE TABLE policy_definition (
                                   id              RAW(16) PRIMARY KEY,
                                   tenant_id       VARCHAR2(100) NOT NULL,
                                   policy_code     VARCHAR2(120) NOT NULL,
                                   policy_version  NUMBER(10) NOT NULL,
                                   policy_type     VARCHAR2(80) NOT NULL,
                                   status          VARCHAR2(30) NOT NULL,
                                   definition      CLOB NOT NULL,
                                   effective_from  TIMESTAMP WITH TIME ZONE,
                                   effective_until TIMESTAMP WITH TIME ZONE,
                                   created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
                                   created_by      VARCHAR2(200) NOT NULL,
                                   CONSTRAINT uk_policy_definition_ver UNIQUE ( tenant_id,
                                                                                policy_code,
                                                                                policy_version ),
                                   CONSTRAINT ck_policy_def_version CHECK ( policy_version > 0 ),
                                   CONSTRAINT ck_policy_def_status CHECK ( status IN ( 'DRAFT', 'ACTIVE', 'RETIRED' ) ),
                                   CONSTRAINT ck_policy_def_effective CHECK ( effective_until IS NULL
                                       OR effective_from IS NULL
                                       OR effective_until > effective_from ),
                                   CONSTRAINT ck_policy_def_def_json CHECK ( definition IS JSON )
);

CREATE INDEX ix_policy_def_active ON
    policy_definition (
                       tenant_id,
                       policy_type,
                       status,
                       effective_from
                       DESC );

CREATE TABLE evidence_upload_session (
                                         id                RAW(16) PRIMARY KEY,
                                         tenant_id         VARCHAR2(100) NOT NULL,
                                         consent_id        RAW(16) NOT NULL,
                                         revision_id       RAW(16) NOT NULL,
                                         evidence_type     VARCHAR2(100) NOT NULL,
                                         original_filename VARCHAR2(300) NOT NULL,
                                         media_type        VARCHAR2(200) NOT NULL,
                                         declared_size     NUMBER(19) NOT NULL,
                                         metadata          CLOB DEFAULT '{}' NOT NULL,
                                         retention_until   TIMESTAMP WITH TIME ZONE,
                                         status            VARCHAR2(30) NOT NULL,
                                         artifact_id       RAW(16),
                                         expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                         created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                         created_by        VARCHAR2(200) NOT NULL,
                                         completed_at      TIMESTAMP WITH TIME ZONE,
                                         cancelled_at      TIMESTAMP WITH TIME ZONE,
                                         CONSTRAINT fk_upload_sess_consent FOREIGN KEY ( consent_id )
                                             REFERENCES consent ( id ),
                                         CONSTRAINT fk_upload_sess_rev FOREIGN KEY ( revision_id )
                                             REFERENCES consent_revision ( id ),
                                         CONSTRAINT fk_upload_sess_art FOREIGN KEY ( artifact_id )
                                             REFERENCES consent_evidence_artifact ( id ),
                                         CONSTRAINT ck_upload_sess_size CHECK ( declared_size >= 0 ),
                                         CONSTRAINT ck_upload_sess_status CHECK ( status IN ( 'CREATED', 'UPLOADING', 'COMPLETED', 'CANCELLED', 'EXPIRED' ) ),
                                         CONSTRAINT ck_upload_sess_expiry CHECK ( expires_at > created_at ),
                                         CONSTRAINT ck_upload_sess_meta_json CHECK ( metadata IS JSON )
);

CREATE INDEX ix_upload_session_expiry ON
    evidence_upload_session (
                             CASE
                                 WHEN
                status
            IN ( 'CREATED', 'UPLOADING' ) THEN
                    status
        END,
                             CASE
                                 WHEN
                status
            IN ( 'CREATED', 'UPLOADING' ) THEN
                    expires_at
        END
        );

CREATE INDEX ix_upload_session_consent ON
    evidence_upload_session (
                             tenant_id,
                             consent_id,
                             created_at
                             DESC );

ALTER TABLE extension_binding ADD (
    client_id      VARCHAR2(200),
    capture_method VARCHAR2(100)
);

CREATE INDEX ix_ext_bind_client ON
    extension_binding (
                       tenant_id,
                       extension_point,
                       client_id,
                       active,
                       execution_order
        );

ALTER TABLE extension_registration ADD (
    configuration_schema CLOB DEFAULT '{}' NOT NULL,
    schema_version       NUMBER(10) DEFAULT 1 NOT NULL
);

ALTER TABLE extension_registration ADD CONSTRAINT ck_ext_reg_schema_json CHECK ( configuration_schema IS JSON );

ALTER TABLE extension_registration ADD CONSTRAINT ck_ext_schema_ver CHECK ( schema_version > 0 );

COMMENT ON TABLE consent_obligation IS
    'Structured post-authorization obligations belonging to an immutable content revision.';

COMMENT ON TABLE policy_definition IS
    'Versioned tenant policy definitions; operational evaluation rows remain in policy_evaluation.';

COMMENT ON TABLE evidence_upload_session IS
    'Staged evidence upload lifecycle to avoid assuming a distributed DB/object-storage transaction.';
ALTER TABLE consent ADD (
    current_revision_id    RAW(16),
    current_revision_no    NUMBER(10),
    decision_id            RAW(16),
    captured_at            TIMESTAMP WITH TIME ZONE,
    captured_by            VARCHAR2(200),
    capture_location       VARCHAR2(300),
    import_batch_reference VARCHAR2(200)
);

CREATE TABLE consent_revision (
                                  id                    RAW(16) PRIMARY KEY,
                                  tenant_id             VARCHAR2(100) NOT NULL,
                                  consent_id            RAW(16) NOT NULL,
                                  revision_no           NUMBER(10) NOT NULL,
                                  purpose_code          VARCHAR2(100) NOT NULL,
                                  presentation_snapshot CLOB DEFAULT '{}' NOT NULL,
                                  content_hash          VARCHAR2(64) NOT NULL,
                                  status                VARCHAR2(30) NOT NULL,
                                  created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
                                  created_by            VARCHAR2(200) NOT NULL,
                                  finalized_at          TIMESTAMP WITH TIME ZONE,
                                  finalized_by          VARCHAR2(200),
                                  superseded_at         TIMESTAMP WITH TIME ZONE,
                                  superseded_by         VARCHAR2(200),
                                  CONSTRAINT fk_consent_revision_consent FOREIGN KEY ( consent_id )
                                      REFERENCES consent ( id ),
                                  CONSTRAINT uk_consent_revision_no UNIQUE ( tenant_id,
                                                                             consent_id,
                                                                             revision_no ),
                                  CONSTRAINT uk_consent_revision_hash UNIQUE ( tenant_id,
                                                                               consent_id,
                                                                               content_hash ),
                                  CONSTRAINT ck_consent_revision_status CHECK ( status IN ( 'DRAFT', 'FINALIZED', 'SUPERSEDED' ) ),
                                  CONSTRAINT ck_consent_revision_finalized CHECK ( ( status = 'DRAFT'
                                      AND finalized_at IS NULL
                                      AND finalized_by IS NULL )
                                      OR ( status IN ( 'FINALIZED', 'SUPERSEDED' )
                                          AND finalized_at IS NOT NULL
                                          AND finalized_by IS NOT NULL ) ),
                                  CONSTRAINT ck_revision_snapshot_json CHECK ( presentation_snapshot IS JSON )
);

CREATE INDEX ix_consent_revision_current ON
    consent_revision (
                      tenant_id,
                      consent_id,
                      revision_no
                      DESC );

CREATE TABLE consent_permission (
                                    id              RAW(16) PRIMARY KEY,
                                    tenant_id       VARCHAR2(100) NOT NULL,
                                    revision_id     RAW(16) NOT NULL,
                                    permission_code VARCHAR2(150) NOT NULL,
                                    attributes      CLOB DEFAULT '{}' NOT NULL,
                                    sort_order      NUMBER(10) DEFAULT 0 NOT NULL,
                                    CONSTRAINT fk_consent_perm_rev FOREIGN KEY ( revision_id )
                                        REFERENCES consent_revision ( id ),
                                    CONSTRAINT uk_consent_permission UNIQUE ( tenant_id,
                                                                              revision_id,
                                                                              permission_code ),
                                    CONSTRAINT ck_consent_perm_attr_json CHECK ( attributes IS JSON )
);

CREATE INDEX ix_consent_perm_rev ON
    consent_permission (
                        tenant_id,
                        revision_id,
                        sort_order,
                        permission_code
        );

CREATE TABLE consent_resource (
                                  id            RAW(16) PRIMARY KEY,
                                  tenant_id     VARCHAR2(100) NOT NULL,
                                  revision_id   RAW(16) NOT NULL,
                                  resource_type VARCHAR2(100) NOT NULL,
                                  resource_id   VARCHAR2(300) NOT NULL,
                                  attributes    CLOB DEFAULT '{}' NOT NULL,
                                  sort_order    NUMBER(10) DEFAULT 0 NOT NULL,
                                  CONSTRAINT fk_consent_res_rev FOREIGN KEY ( revision_id )
                                      REFERENCES consent_revision ( id ),
                                  CONSTRAINT uk_consent_resource UNIQUE ( tenant_id,
                                                                          revision_id,
                                                                          resource_type,
                                                                          resource_id ),
                                  CONSTRAINT ck_consent_res_attr_json CHECK ( attributes IS JSON )
);

CREATE INDEX ix_consent_res_rev ON
    consent_resource (
                      tenant_id,
                      revision_id,
                      sort_order,
                      resource_type,
                      resource_id
        );

CREATE TABLE consent_constraint (
                                    id               RAW(16) PRIMARY KEY,
                                    tenant_id        VARCHAR2(100) NOT NULL,
                                    revision_id      RAW(16) NOT NULL,
                                    constraint_type  VARCHAR2(100) NOT NULL,
                                    operator         VARCHAR2(40) NOT NULL,
                                    constraint_value CLOB NOT NULL,
                                    sort_order       NUMBER(10) DEFAULT 0 NOT NULL,
                                    CONSTRAINT fk_consent_const_rev FOREIGN KEY ( revision_id )
                                        REFERENCES consent_revision ( id ),
                                    CONSTRAINT ck_consent_const_val_json CHECK ( constraint_value IS JSON )
);

CREATE INDEX ix_consent_const_rev ON
    consent_constraint (
                        tenant_id,
                        revision_id,
                        sort_order,
                        constraint_type
        );

CREATE TABLE consent_decision (
                                  id                      RAW(16) PRIMARY KEY,
                                  tenant_id               VARCHAR2(100) NOT NULL,
                                  consent_id              RAW(16) NOT NULL,
                                  revision_id             RAW(16) NOT NULL,
                                  outcome                 VARCHAR2(30) NOT NULL,
                                  decision_maker_id       VARCHAR2(200) NOT NULL,
                                  captured_by             VARCHAR2(200) NOT NULL,
                                  capture_method          VARCHAR2(60) NOT NULL,
                                  capture_location        VARCHAR2(300),
                                  authorization_reference VARCHAR2(300),
                                  authentication_context  CLOB DEFAULT '{}' NOT NULL,
                                  decided_at              TIMESTAMP WITH TIME ZONE NOT NULL,
                                  created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
                                  CONSTRAINT fk_consent_dec_consent FOREIGN KEY ( consent_id )
                                      REFERENCES consent ( id ),
                                  CONSTRAINT fk_consent_dec_rev FOREIGN KEY ( revision_id )
                                      REFERENCES consent_revision ( id ),
                                  CONSTRAINT ck_consent_dec_outcome CHECK ( outcome IN ( 'AUTHORIZED', 'REJECTED' ) ),
                                  CONSTRAINT ck_consent_dec_auth_ctx_json CHECK ( authentication_context IS JSON )
);

CREATE INDEX ix_consent_dec_consent ON
    consent_decision (
                      tenant_id,
                      consent_id,
                      decided_at
                      DESC );

ALTER TABLE consent
    ADD CONSTRAINT fk_consent_curr_rev FOREIGN KEY ( current_revision_id )
        REFERENCES consent_revision ( id );

ALTER TABLE consent
    ADD CONSTRAINT fk_consent_decision FOREIGN KEY ( decision_id )
        REFERENCES consent_decision ( id );

ALTER TABLE consent
    ADD CONSTRAINT ck_consent_rev_ref CHECK ( ( current_revision_id IS NULL
        AND current_revision_no IS NULL )
        OR ( current_revision_id IS NOT NULL
            AND current_revision_no IS NOT NULL
            AND current_revision_no > 0 ) );

COMMENT ON TABLE consent_revision IS
    'Immutable structured consent content revision. A new revision supersedes rather than overwrites.';

COMMENT ON TABLE consent_decision IS
    'Immutable decision capture bound to the exact consent content revision.';
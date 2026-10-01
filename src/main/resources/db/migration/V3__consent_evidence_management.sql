CREATE TABLE consent_evidence_bundle (
                                         id              RAW(16) PRIMARY KEY,
                                         tenant_id       VARCHAR2(100) NOT NULL,
                                         consent_id      RAW(16) NOT NULL,
                                         revision_id     RAW(16) NOT NULL,
                                         evidence_type   VARCHAR2(80) NOT NULL,
                                         status          VARCHAR2(40) NOT NULL,
                                         retention_until TIMESTAMP WITH TIME ZONE,
                                         legal_hold      NUMBER(1) DEFAULT 0 NOT NULL,
                                         created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
                                         created_by      VARCHAR2(200) NOT NULL,
                                         verified_at     TIMESTAMP WITH TIME ZONE,
                                         verified_by     VARCHAR2(200),
                                         updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
                                         updated_by      VARCHAR2(200) NOT NULL,
                                         version         NUMBER(19) DEFAULT 0 NOT NULL,
                                         CONSTRAINT fk_ev_bundle_consent FOREIGN KEY ( consent_id )
                                             REFERENCES consent ( id ),
                                         CONSTRAINT fk_ev_bundle_revision FOREIGN KEY ( revision_id )
                                             REFERENCES consent_revision ( id ),
                                         CONSTRAINT uk_ev_bundle_revision UNIQUE ( tenant_id,
                                                                                   consent_id,
                                                                                   revision_id ),
                                         CONSTRAINT ck_ev_bundle_status CHECK ( status IN ( 'PENDING', 'VERIFIED', 'REJECTED', 'SUPERSEDED', 'EXPIRED' ) ),
                                         CONSTRAINT ck_ev_bundle_legal_hold CHECK ( legal_hold IN ( 0, 1 ) )
);

CREATE INDEX ix_ev_bundle_consent ON
    consent_evidence_bundle (
                             tenant_id,
                             consent_id,
                             created_at
                             DESC );

CREATE TABLE consent_evidence_artifact (
                                           id                     RAW(16) PRIMARY KEY,
                                           tenant_id              VARCHAR2(100) NOT NULL,
                                           bundle_id              RAW(16) NOT NULL,
                                           evidence_type          VARCHAR2(80) NOT NULL,
                                           storage_provider       VARCHAR2(50) NOT NULL,
                                           storage_container      VARCHAR2(200),
                                           object_key             VARCHAR2(700) NOT NULL,
                                           original_filename      VARCHAR2(300) NOT NULL,
                                           media_type             VARCHAR2(200) NOT NULL,
                                           size_bytes             NUMBER(19) NOT NULL,
                                           sha256                 VARCHAR2(64) NOT NULL,
                                           status                 VARCHAR2(40) NOT NULL,
                                           malware_scan_status    VARCHAR2(40) NOT NULL,
                                           supersedes_artifact_id RAW(16),
                                           metadata               CLOB DEFAULT '{}' NOT NULL,
                                           created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
                                           created_by             VARCHAR2(200) NOT NULL,
                                           superseded_at          TIMESTAMP WITH TIME ZONE,
                                           superseded_by          VARCHAR2(200),
                                           CONSTRAINT fk_ev_art_bundle FOREIGN KEY ( bundle_id )
                                               REFERENCES consent_evidence_bundle ( id ),
                                           CONSTRAINT fk_ev_art_supersedes FOREIGN KEY ( supersedes_artifact_id )
                                               REFERENCES consent_evidence_artifact ( id ),
                                           CONSTRAINT uk_ev_art_hash UNIQUE ( tenant_id,
                                                                              bundle_id,
                                                                              sha256 ),
                                           CONSTRAINT ck_ev_art_size CHECK ( size_bytes >= 0 ),
                                           CONSTRAINT ck_ev_art_status CHECK ( status IN ( 'ACTIVE', 'SUPERSEDED', 'QUARANTINED', 'DELETED' ) ),
                                           CONSTRAINT ck_ev_malware_status CHECK ( malware_scan_status IN ( 'NOT_CONFIGURED', 'PENDING', 'CLEAN', 'INFECTED', 'FAILED' ) ),
                                           CONSTRAINT ck_ev_art_meta_json CHECK ( metadata IS JSON )
);

CREATE INDEX ix_ev_art_bundle ON
    consent_evidence_artifact (
                               tenant_id,
                               bundle_id,
                               created_at
                               DESC );

CREATE TABLE consent_evidence_verification (
                                               id                  RAW(16) PRIMARY KEY,
                                               tenant_id           VARCHAR2(100) NOT NULL,
                                               bundle_id           RAW(16) NOT NULL,
                                               artifact_id         RAW(16),
                                               result              VARCHAR2(40) NOT NULL,
                                               verifier_id         VARCHAR2(200) NOT NULL,
                                               verification_method VARCHAR2(100) NOT NULL,
                                               reason_code         VARCHAR2(100),
                                               reason_detail       VARCHAR2(1000),
                                               verification_data   CLOB DEFAULT '{}' NOT NULL,
                                               verified_at         TIMESTAMP WITH TIME ZONE NOT NULL,
                                               CONSTRAINT fk_ev_verif_bundle FOREIGN KEY ( bundle_id )
                                                   REFERENCES consent_evidence_bundle ( id ),
                                               CONSTRAINT fk_ev_verif_artifact FOREIGN KEY ( artifact_id )
                                                   REFERENCES consent_evidence_artifact ( id ),
                                               CONSTRAINT ck_ev_verif_result CHECK ( result IN ( 'VERIFIED', 'REJECTED' ) ),
                                               CONSTRAINT ck_ev_verif_data_json CHECK ( verification_data IS JSON )
);

CREATE INDEX ix_ev_verif_bundle ON
    consent_evidence_verification (
                                   tenant_id,
                                   bundle_id,
                                   verified_at
                                   DESC );

CREATE TABLE evidence_access_log (
                                     id             RAW(16) PRIMARY KEY,
                                     tenant_id      VARCHAR2(100) NOT NULL,
                                     consent_id     RAW(16) NOT NULL,
                                     bundle_id      RAW(16) NOT NULL,
                                     artifact_id    RAW(16),
                                     action         VARCHAR2(40) NOT NULL,
                                     actor_id       VARCHAR2(200) NOT NULL,
                                     actor_type     VARCHAR2(40) NOT NULL,
                                     source_system  VARCHAR2(100) NOT NULL,
                                     correlation_id VARCHAR2(100) NOT NULL,
                                     occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
                                     CONSTRAINT ck_evidence_access_action CHECK ( action IN ( 'UPLOAD', 'VIEW_METADATA', 'DOWNLOAD', 'VERIFY', 'REJECT',
                                         'SUPERSEDE', 'LEGAL_HOLD' ) )
    );

CREATE INDEX ix_ev_access_consent ON
    evidence_access_log (
                         tenant_id,
                         consent_id,
                         occurred_at
                         DESC );

ALTER TABLE consent ADD (
    evidence_bundle_id RAW(16)
);

ALTER TABLE consent
    ADD CONSTRAINT fk_consent_ev_bundle FOREIGN KEY ( evidence_bundle_id )
        REFERENCES consent_evidence_bundle ( id );

COMMENT ON TABLE consent_evidence_artifact IS
    'Artifact metadata only. Binary content is stored outside PostgreSQL.';

COMMENT ON TABLE evidence_access_log IS
    'Append-only chain-of-custody access log for evidence operations.';
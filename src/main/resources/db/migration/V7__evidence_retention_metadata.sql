ALTER TABLE consent_evidence_artifact ADD (
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR2(200)
);

ALTER TABLE evidence_access_log DROP CONSTRAINT ck_evidence_access_action;

ALTER TABLE evidence_access_log
    ADD CONSTRAINT ck_evidence_access_action CHECK ( action IN ( 'UPLOAD', 'VIEW_METADATA', 'DOWNLOAD', 'VERIFY', 'REJECT',
    'SUPERSEDE', 'LEGAL_HOLD', 'RETENTION_DELETE' ) );

CREATE INDEX ix_evidence_retention_cand ON
    consent_evidence_bundle (
                             CASE
                                 WHEN
                retention_until
            IS NOT NULL
                AND
                legal_hold
            = 0 THEN
                    retention_until
        END
        );
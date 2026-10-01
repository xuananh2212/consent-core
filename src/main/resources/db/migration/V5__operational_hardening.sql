CREATE INDEX ix_consent_search_type_status_time ON
    consent (
             tenant_id,
             consent_type,
             status,
             created_at
             DESC);

CREATE INDEX ix_consent_external_request ON
    consent (
             CASE WHEN
                external_request_id
            IS NOT NULL THEN
                    tenant_id
        END,
             external_request_id
        );

CREATE INDEX ix_consent_decision_reference ON
    consent (
             CASE WHEN
                decision_id
            IS NOT NULL THEN
                    tenant_id
        END,
             decision_id
        );

CREATE INDEX ix_audit_time ON
    audit_event (
                 occurred_at
        );

CREATE INDEX ix_outbox_published_cleanup ON
    outbox_event (
                  CASE WHEN
                status
            = 'PUBLISHED' THEN
                    published_at
        END
        );

COMMENT
ON COLUMN consent.current_revision_id IS
    'Exact immutable content revision currently bound to the consent.';

COMMENT
ON COLUMN consent.decision_id IS
    'Decision capture for the current authorized/rejected outcome.';

COMMENT
ON COLUMN consent.evidence_bundle_id IS
    'Current evidence bundle reference; artifact binaries remain external.';
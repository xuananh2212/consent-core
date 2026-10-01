-- Flow Manager v0.2 integration provenance. Additive only: V1-V9 remain immutable.
CREATE TABLE consent_authentication_evidence (
                                                 id         RAW(16) PRIMARY KEY,
                                                 tenant_id  VARCHAR2(100) NOT NULL,
                                                 consent_id RAW(16) NOT NULL REFERENCES consent(id),
                                                 decision_id              RAW(16) NOT NULL REFERENCES consent_decision(id),
                                                 subject_ref              VARCHAR2(200) NOT NULL,
                                                 source_system            VARCHAR2(100) NOT NULL,
                                                 source_interaction_ref   VARCHAR2(200),
                                                 authorization_server_ref VARCHAR2(150),
                                                 as_transaction_ref       VARCHAR2(300),
                                                 issuer                   VARCHAR2(300) NOT NULL,
                                                 authentication_time      TIMESTAMP WITH TIME ZONE NOT NULL,
                                                 acr                      VARCHAR2(300),
                                                 amr                      CLOB DEFAULT '[]' NOT NULL,
                                                 evidence_ref             VARCHAR2(500),
                                                 evidence_hash            VARCHAR2(200),
                                                 verification_profile    VARCHAR2(200) NOT NULL,
                                                 created_at               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                                 CONSTRAINT ck_consent_auth_ev_amr_json CHECK (amr IS JSON)
);

CREATE INDEX idx_consent_auth_ev_decision
    ON consent_authentication_evidence(tenant_id, consent_id, decision_id);

CREATE INDEX idx_consent_auth_ev_interact
    ON consent_authentication_evidence(
                                       CASE WHEN source_interaction_ref IS NOT NULL THEN tenant_id END,
                                       CASE WHEN source_interaction_ref IS NOT NULL THEN source_system END,
                                       source_interaction_ref
        );

CREATE INDEX idx_consent_auth_ev_as_trans
    ON consent_authentication_evidence(
                                       CASE WHEN as_transaction_ref IS NOT NULL THEN tenant_id END,
                                       CASE WHEN as_transaction_ref IS NOT NULL THEN authorization_server_ref END,
                                       as_transaction_ref
        );

CREATE OR REPLACE TRIGGER trg_consent_auth_ev_app_only
BEFORE UPDATE OR DELETE ON consent_authentication_evidence
    FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20015, 'consent_authentication_evidence is append-only');
END;
/

CREATE OR REPLACE TRIGGER trg_consent_auth_ev_ref_int
BEFORE INSERT ON consent_authentication_evidence
FOR EACH ROW
DECLARE
v_count NUMBER;
BEGIN
SELECT
    COUNT(*)
INTO v_count
FROM
    consent_decision d
        JOIN consent c ON c.id = d.consent_id
WHERE
    d.id = :new.decision_id
  AND d.tenant_id = :new.tenant_id
  AND d.consent_id = :new.consent_id
  AND c.tenant_id = :new.tenant_id
  AND c.id = :new.consent_id;

IF v_count = 0 THEN
    raise_application_error(-20016, 'Authentication evidence does not belong to consent/decision/tenant');
END IF;

end;
/

COMMENT ON TABLE consent_authentication_evidence IS
    'Append-only normalized authentication and interaction provenance for a consent decision. Raw IAM tokens are forbidden.';

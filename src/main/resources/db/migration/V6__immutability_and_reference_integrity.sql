ALTER TABLE trusted_consent_source DROP CONSTRAINT uk_trusted_consent_source;

CREATE UNIQUE INDEX uk_trusted_consent_source_norm ON
    trusted_consent_source (
                            tenant_id,
                            source_system,
                            nvl(
        client_id, ' '));

CREATE
OR REPLACE TRIGGER trg_consent_hist_append_only BEFORE
UPDATE OR
DELETE
ON consent_status_history
    FOR EACH ROW
BEGIN
    raise_application_error
(-20001, 'consent_status_history is append-only');
END;
/

CREATE
OR REPLACE TRIGGER trg_audit_event_append_only BEFORE
UPDATE OR
DELETE
ON audit_event
    FOR EACH ROW
BEGIN
    raise_application_error
(-20002, 'audit_event is append-only');
END;
/

CREATE
OR REPLACE TRIGGER trg_consent_dec_append_only BEFORE
UPDATE OR
DELETE
ON consent_decision
    FOR EACH ROW
BEGIN
    raise_application_error
(-20003, 'consent_decision is append-only');
END;
/

CREATE
OR REPLACE TRIGGER trg_ev_verif_append_only BEFORE
UPDATE OR
DELETE
ON consent_evidence_verification
    FOR EACH ROW
BEGIN
    raise_application_error
(-20004, 'consent_evidence_verification is append-only');
END;
/

CREATE
OR REPLACE TRIGGER trg_ev_access_append_only BEFORE
UPDATE OR
DELETE
ON evidence_access_log
    FOR EACH ROW
BEGIN
    raise_application_error
(-20005, 'evidence_access_log is append-only');
END;
/

CREATE
OR REPLACE TRIGGER trg_consent_rev_immutable BEFORE
UPDATE ON consent_revision
    FOR EACH ROW
BEGIN
    IF
:old.status = 'DRAFT'
        AND :new.status = 'FINALIZED'
        AND :new.finalized_at IS NOT NULL
        AND :new.finalized_by IS NOT NULL
        AND :old.id = :new.id
        AND :old.tenant_id = :new.tenant_id
        AND :old.consent_id = :new.consent_id
        AND :old.revision_no = :new.revision_no
        AND :old.purpose_code = :new.purpose_code
        AND :old.content_hash = :new.content_hash
    THEN
        NULL;
    ELSIF
:old.status = 'FINALIZED'
        AND :new.status = 'SUPERSEDED'
        AND :new.superseded_at IS NOT NULL
        AND :new.superseded_by IS NOT NULL
        AND :old.id = :new.id
        AND :old.tenant_id = :new.tenant_id
        AND :old.consent_id = :new.consent_id
        AND :old.revision_no = :new.revision_no
        AND :old.purpose_code = :new.purpose_code
        AND :old.content_hash = :new.content_hash
        AND :old.created_at = :new.created_at
        AND :old.created_by = :new.created_by
        AND :old.finalized_at = :new.finalized_at
        AND :old.finalized_by = :new.finalized_by
    THEN
        NULL;
ELSE
        raise_application_error(-20006, 'Consent revision is immutable');
END IF;
END;
/

CREATE
OR REPLACE TRIGGER trg_consent_rev_no_delete BEFORE
    DELETE
ON consent_revision
    FOR EACH ROW
BEGIN
    raise_application_error
(-20007, 'consent_revision is append-only');
END;
/

CREATE
OR REPLACE TRIGGER trg_perm_rev_guard BEFORE
    INSERT OR
UPDATE OR
DELETE
ON consent_permission
    FOR EACH ROW
DECLARE
v_target_revision RAW(16);
    v_revision_status
VARCHAR2(30);
BEGIN
    IF
deleting THEN
        v_target_revision := :old.revision_id;
ELSE
        v_target_revision := :new.revision_id;
END IF;

SELECT status
INTO v_revision_status
FROM consent_revision
WHERE id = v_target_revision;

IF
v_revision_status <> 'DRAFT' THEN
        raise_application_error(-20008, 'Content of finalized consent revision is immutable');
END IF;
EXCEPTION
    WHEN no_data_found THEN
        NULL;
END;
/

CREATE
OR REPLACE TRIGGER trg_res_rev_guard BEFORE
    INSERT OR
UPDATE OR
DELETE
ON consent_resource
    FOR EACH ROW
DECLARE
v_target_revision RAW(16);
    v_revision_status
VARCHAR2(30);
BEGIN
    IF
deleting THEN
        v_target_revision := :old.revision_id;
ELSE
        v_target_revision := :new.revision_id;
END IF;

SELECT status
INTO v_revision_status
FROM consent_revision
WHERE id = v_target_revision;

IF
v_revision_status <> 'DRAFT' THEN
        raise_application_error(-20009, 'Content of finalized consent revision is immutable');
END IF;
EXCEPTION
    WHEN no_data_found THEN
        NULL;
END;
/

CREATE
OR REPLACE TRIGGER trg_const_rev_guard BEFORE
    INSERT OR
UPDATE OR
DELETE
ON consent_constraint
    FOR EACH ROW
DECLARE
v_target_revision RAW(16);
    v_revision_status
VARCHAR2(30);
BEGIN
    IF
deleting THEN
        v_target_revision := :old.revision_id;
ELSE
        v_target_revision := :new.revision_id;
END IF;

SELECT status
INTO v_revision_status
FROM consent_revision
WHERE id = v_target_revision;

IF
v_revision_status <> 'DRAFT' THEN
        raise_application_error(-20010, 'Content of finalized consent revision is immutable');
END IF;
EXCEPTION
    WHEN no_data_found THEN
        NULL;
END;
/

CREATE
OR REPLACE TRIGGER trg_consent_ref_integrity BEFORE
    INSERT OR
UPDATE OF current_revision_id, current_revision_no, decision_id, evidence_bundle_id
ON consent
    FOR EACH ROW
DECLARE
v_count NUMBER;
BEGIN
    IF
:new.current_revision_id IS NOT NULL THEN
SELECT COUNT(*)
INTO v_count
FROM consent_revision r
WHERE r.id = :new.current_revision_id
  AND r.tenant_id = :new.tenant_id
  AND r.consent_id = :new.id
  AND r.revision_no = :new.current_revision_no
  AND r.status IN ('FINALIZED', 'SUPERSEDED');

IF
v_count = 0 THEN
            raise_application_error(-20011, 'Current revision does not belong to consent/tenant');
END IF;
END IF;

    IF
:new.decision_id IS NOT NULL THEN
SELECT COUNT(*)
INTO v_count
FROM consent_decision d
WHERE d.id = :new.decision_id
  AND d.tenant_id = :new.tenant_id
  AND d.consent_id = :new.id
  AND d.revision_id = :new.current_revision_id;

IF
v_count = 0 THEN
            raise_application_error(-20012, 'Decision does not belong to current consent revision');
END IF;
END IF;

    IF
:new.evidence_bundle_id IS NOT NULL THEN
SELECT COUNT(*)
INTO v_count
FROM consent_evidence_bundle b
WHERE b.id = :new.evidence_bundle_id
  AND b.tenant_id = :new.tenant_id
  AND b.consent_id = :new.id
  AND b.revision_id = :new.current_revision_id;

IF
v_count = 0 THEN
            raise_application_error(-20013, 'Evidence bundle does not belong to current consent revision');
END IF;
END IF;

END;
/
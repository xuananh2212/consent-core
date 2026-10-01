CREATE
OR REPLACE TRIGGER trg_consent_subject_bind_once BEFORE
UPDATE OF subject_id
ON consent
    FOR EACH ROW
BEGIN
    IF :OLD.subject_id IS NOT NULL
       AND (:NEW.subject_id IS NULL OR :NEW.subject_id <> :OLD.subject_id) THEN
        raise_application_error(-20031, 'consent.subject_id is immutable once bound');
END IF;
END;
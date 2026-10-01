-- Post-authentication eligibility gate and controlled requirement selectors.
-- V11 remains immutable. This additive migration extends Data Requirement applicability with optional
-- clientId and required OAuth scopes. Business selection stays deliberately constrained; no script/expression engine.

ALTER TABLE consent_data_requirement ADD (
    client_id       VARCHAR2(200),
    required_scopes CLOB DEFAULT '[]' NOT NULL
);

ALTER TABLE consent_data_requirement ADD CONSTRAINT ck_data_req_scopes_json CHECK ( required_scopes IS JSON );

-- V11 allowed a single requirement_code per tenant/type/phase. v0.5 permits selector-specific variants.
ALTER TABLE consent_data_requirement DROP CONSTRAINT uk_data_requirement;

-- required_scopes is normalized/sorted by the configuration service before persistence.
-- md5(required_scopes) keeps the unique index compact while preserving deterministic selector uniqueness.
CREATE UNIQUE INDEX uk_data_req_selector ON
    consent_data_requirement (
                              tenant_id,
                              consent_type_code,
                              resolution_phase,
                              requirement_code,
                              nvl(client_id, ' '),
                              rawtohex(standard_hash(to_char(required_scopes), 'MD5')));

CREATE INDEX ix_data_req_eff_lookup ON
    consent_data_requirement (
                              tenant_id,
                              consent_type_code,
                              resolution_phase,
                              active,
                              client_id,
                              requirement_code
        );

COMMENT ON COLUMN consent_data_requirement.client_id IS
    'Optional requesting-client selector. NULL means the requirement applies to every client.';

COMMENT ON COLUMN consent_data_requirement.required_scopes IS
    'Optional OAuth scope selector. Empty array means any scope; all configured values must be present in effective trusted scopes.';
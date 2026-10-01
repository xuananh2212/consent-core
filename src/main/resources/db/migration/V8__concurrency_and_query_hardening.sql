-- Object keys must identify one artifact metadata row.
CREATE UNIQUE INDEX uk_evidence_artifact_obj_key ON
    consent_evidence_artifact (
                               storage_provider,
                               nvl(
        storage_container, ' '),
                               object_key
        );

CREATE INDEX ix_audit_tenant_action_time ON
    audit_event (
                 tenant_id,
                 action,
                 occurred_at
                 DESC );

CREATE INDEX ix_audit_tenant_actor_time ON
    audit_event (
                 tenant_id,
                 actor_id,
                 occurred_at
                 DESC );

CREATE INDEX ix_policy_eval_tenant_op_time ON
    policy_evaluation (
                       tenant_id,
                       operation,
                       evaluated_at
                       DESC );

COMMENT ON TABLE consent_idempotency IS
    'Idempotency results. Registration also takes a PostgreSQL transaction advisory lock before lookup/write.';
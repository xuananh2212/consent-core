package vn.com.fis.consentcore.registry.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;
import vn.com.fis.consentcore.registry.domain.model.Consent;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;
import vn.com.fis.consentcore.registry.domain.model.EvidenceStatus;

@Entity
@Table(name = "consent")
class ConsentJpaEntity {
    @Id UUID id;
    @Column(name = "tenant_id", nullable = false, length = 100) String tenantId;
    @Column(name = "external_request_id", length = 200) String externalRequestId;
    @Column(name = "consent_type", nullable = false, length = 100) String consentType;
    @Column(name = "subject_id", length = 200) String subjectId;
    @Column(name = "client_id", nullable = false, length = 200) String clientId;
    @Column(name = "purpose", length = 500) String purpose;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 60) ConsentStatus status;
    @Column(name = "valid_from", nullable = false) Instant validFrom;
    @Column(name = "valid_until", nullable = false) Instant validUntil;
    @Enumerated(EnumType.STRING) @Column(name = "acquisition_channel", nullable = false, length = 60)
    AcquisitionChannel acquisitionChannel;
    @Enumerated(EnumType.STRING) @Column(name = "capture_method", nullable = false, length = 60)
    CaptureMethod captureMethod;
    @Column(name = "source_system", nullable = false, length = 100) String sourceSystem;
    @Column(name = "external_consent_id", length = 200) String externalConsentId;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_policy", nullable = false, length = 40)
    EvidencePolicy evidencePolicy;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_status", nullable = false, length = 40)
    EvidenceStatus evidenceStatus;
    @Column(name = "trusted_source_authorization", nullable = false) boolean trustedSourceAuthorization;
    @Column(name = "authorization_reference", length = 300) String authorizationReference;
    @Column(name = "captured_at") Instant capturedAt;
    @Column(name = "captured_by", length = 200) String capturedBy;
    @Column(name = "capture_location", length = 300) String captureLocation;
    @Column(name = "import_batch_reference", length = 200) String importBatchReference;
    @Column(name = "current_revision_id") UUID currentRevisionId;
    @Column(name = "current_revision_no") Integer currentRevisionNo;
    @Column(name = "decision_id") UUID decisionId;
    @Column(name = "evidence_bundle_id") UUID evidenceBundleId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "created_by", nullable = false, length = 200) String createdBy;
    @Column(name = "updated_by", nullable = false, length = 200) String updatedBy;
    @Version @Column(name = "version", nullable = false) Long version;

    protected ConsentJpaEntity() {
    }

    static ConsentJpaEntity fromDomain(Consent consent) {
        ConsentJpaEntity entity = new ConsentJpaEntity();
        entity.id = consent.id();
        entity.apply(consent);
        return entity;
    }

    void apply(Consent consent) {
        tenantId = consent.tenantId();
        externalRequestId = consent.externalRequestId();
        consentType = consent.consentType();
        subjectId = consent.subjectId();
        clientId = consent.clientId();
        purpose = consent.purpose();
        status = consent.status();
        validFrom = consent.validFrom();
        validUntil = consent.validUntil();
        acquisitionChannel = consent.acquisitionChannel();
        captureMethod = consent.captureMethod();
        sourceSystem = consent.sourceSystem();
        externalConsentId = consent.externalConsentId();
        evidencePolicy = consent.evidencePolicy();
        evidenceStatus = consent.evidenceStatus();
        trustedSourceAuthorization = consent.trustedSourceAuthorization();
        authorizationReference = consent.authorizationReference();
        capturedAt = consent.capturedAt();
        capturedBy = consent.capturedBy();
        captureLocation = consent.captureLocation();
        importBatchReference = consent.importBatchReference();
        currentRevisionId = consent.currentRevisionId();
        currentRevisionNo = consent.currentRevisionNo();
        decisionId = consent.decisionId();
        evidenceBundleId = consent.evidenceBundleId();
        createdAt = consent.createdAt();
        updatedAt = consent.updatedAt();
        createdBy = consent.createdBy();
        updatedBy = consent.updatedBy();
    }

    Consent toDomain() {
        return Consent.rehydrate(
                id, tenantId, externalRequestId, consentType, subjectId, clientId, purpose, status,
                validFrom, validUntil, acquisitionChannel, captureMethod, sourceSystem, externalConsentId,
                evidencePolicy, evidenceStatus, trustedSourceAuthorization, authorizationReference,
                capturedAt, capturedBy, captureLocation, importBatchReference,
                currentRevisionId, currentRevisionNo, decisionId, evidenceBundleId,
                createdAt, updatedAt, createdBy, updatedBy, version == null ? 0L : version);
    }
}

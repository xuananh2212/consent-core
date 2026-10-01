package vn.com.fis.consentcore.registry.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.event.ConsentAuthorizationRequestedEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentAuthorizedEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentCancelledEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentContentRevisionLinkedEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentDecisionCapturedEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentEvidenceStatusChangedEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentExpiredEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentRegisteredEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentRejectedEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentRevokedEvent;
import vn.com.fis.consentcore.registry.domain.event.ConsentSuspendedEvent;
import vn.com.fis.consentcore.registry.domain.exception.ConsentContentNotFinalizedException;
import vn.com.fis.consentcore.registry.domain.exception.ConsentNotYetExpiredException;
import vn.com.fis.consentcore.registry.domain.exception.DecisionCaptureRequiredException;
import vn.com.fis.consentcore.registry.domain.exception.EvidenceNotVerifiedException;
import vn.com.fis.consentcore.registry.domain.exception.InvalidConsentStateTransitionException;
import vn.com.fis.consentcore.registry.domain.exception.InvalidConsentValidityException;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

public final class Consent {
    private final UUID id;
    private final String tenantId;
    private final String externalRequestId;
    private final String consentType;
    private String subjectId;
    private final String clientId;
    private final String purpose;
    private ConsentStatus status;
    private final Instant validFrom;
    private final Instant validUntil;
    private final AcquisitionChannel acquisitionChannel;
    private final CaptureMethod captureMethod;
    private final String sourceSystem;
    private final String externalConsentId;
    private final EvidencePolicy evidencePolicy;
    private EvidenceStatus evidenceStatus;
    private final boolean trustedSourceAuthorization;
    private String authorizationReference;
    private final Instant capturedAt;
    private final String capturedBy;
    private final String captureLocation;
    private final String importBatchReference;
    private UUID currentRevisionId;
    private Integer currentRevisionNo;
    private UUID decisionId;
    private UUID evidenceBundleId;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private long version;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();
    private final List<ConsentTransition> pendingTransitions = new ArrayList<>();

    private Consent(
            UUID id,
            String tenantId,
            String externalRequestId,
            String consentType,
            String subjectId,
            String clientId,
            String purpose,
            ConsentStatus status,
            Instant validFrom,
            Instant validUntil,
            AcquisitionChannel acquisitionChannel,
            CaptureMethod captureMethod,
            String sourceSystem,
            String externalConsentId,
            EvidencePolicy evidencePolicy,
            EvidenceStatus evidenceStatus,
            boolean trustedSourceAuthorization,
            String authorizationReference,
            Instant capturedAt,
            String capturedBy,
            String captureLocation,
            String importBatchReference,
            UUID currentRevisionId,
            Integer currentRevisionNo,
            UUID decisionId,
            UUID evidenceBundleId,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = requireText(tenantId, "tenantId", 100);
        this.externalRequestId = trimToNull(externalRequestId, "externalRequestId", 200);
        this.consentType = requireText(consentType, "consentType", 100);
        this.subjectId = trimToNull(subjectId, "subjectId", 200);
        this.clientId = requireText(clientId, "clientId", 200);
        this.purpose = trimToNull(purpose, "purpose", 500);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom must not be null");
        this.validUntil = Objects.requireNonNull(validUntil, "validUntil must not be null");
        this.acquisitionChannel = Objects.requireNonNull(acquisitionChannel, "acquisitionChannel must not be null");
        this.captureMethod = Objects.requireNonNull(captureMethod, "captureMethod must not be null");
        this.sourceSystem = requireText(sourceSystem, "sourceSystem", 100);
        this.externalConsentId = trimToNull(externalConsentId, "externalConsentId", 200);
        this.evidencePolicy = Objects.requireNonNull(evidencePolicy, "evidencePolicy must not be null");
        this.evidenceStatus = Objects.requireNonNull(evidenceStatus, "evidenceStatus must not be null");
        this.trustedSourceAuthorization = trustedSourceAuthorization;
        this.authorizationReference = trimToNull(authorizationReference, "authorizationReference", 300);
        this.capturedAt = capturedAt;
        this.capturedBy = trimToNull(capturedBy, "capturedBy", 200);
        this.captureLocation = trimToNull(captureLocation, "captureLocation", 300);
        this.importBatchReference = trimToNull(importBatchReference, "importBatchReference", 200);
        this.currentRevisionId = currentRevisionId;
        this.currentRevisionNo = currentRevisionNo;
        this.decisionId = decisionId;
        this.evidenceBundleId = evidenceBundleId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.createdBy = requireText(createdBy, "createdBy", 200);
        this.updatedBy = requireText(updatedBy, "updatedBy", 200);
        this.version = version;
        validateReferencePairs();
    }

    public static Consent register(ConsentRegistrationData data, String actorId, Instant now) {
        Objects.requireNonNull(data, "registration data must not be null");
        actorId = requireText(actorId, "actorId");
        now = Objects.requireNonNull(now, "now must not be null");
        validateValidity(data.validFrom(), data.validUntil());
        if (data.trustedSourceAuthorization() && data.evidencePolicy() != EvidencePolicy.NOT_REQUIRED) {
            throw new IllegalArgumentException("trusted source auto-authorization requires evidencePolicy NOT_REQUIRED");
        }
        if ((data.acquisitionChannel() == AcquisitionChannel.PARTNER
                || data.acquisitionChannel() == AcquisitionChannel.LEGACY_IMPORT)
                && trimToNull(data.externalConsentId()) == null) {
            throw new IllegalArgumentException("externalConsentId is required for partner or legacy import");
        }
        if (data.acquisitionChannel() == AcquisitionChannel.LEGACY_IMPORT
                && trimToNull(data.importBatchReference()) == null) {
            throw new IllegalArgumentException("importBatchReference is required for legacy import");
        }

        UUID id = UUID.randomUUID();
        EvidenceStatus initialEvidenceStatus = data.evidencePolicy() == EvidencePolicy.NOT_REQUIRED
                ? EvidenceStatus.NOT_REQUIRED : EvidenceStatus.PENDING;
        Instant capturedAt = data.capturedAt() == null ? now : data.capturedAt();
        String capturedBy = defaultText(data.capturedBy(), actorId);
        Consent consent = new Consent(
                id, data.tenantId(), data.externalRequestId(), data.consentType(), data.subjectId(),
                data.clientId(), data.purpose(), ConsentStatus.REGISTERED, data.validFrom(), data.validUntil(),
                data.acquisitionChannel(), data.captureMethod(), data.sourceSystem(), data.externalConsentId(),
                data.evidencePolicy(), initialEvidenceStatus, data.trustedSourceAuthorization(), null,
                capturedAt, capturedBy, data.captureLocation(), data.importBatchReference(),
                null, null, null, null, now, now, actorId, actorId, 0L);
        consent.pendingTransitions.add(new ConsentTransition(
                UUID.randomUUID(), id, data.tenantId(), null, ConsentStatus.REGISTERED,
                "CONSENT_REGISTERED", null, now));
        consent.pendingEvents.add(new ConsentRegisteredEvent(
                UUID.randomUUID(), data.tenantId(), id, actorId, data.consentType(), now));
        return consent;
    }

    public static Consent rehydrate(
            UUID id,
            String tenantId,
            String externalRequestId,
            String consentType,
            String subjectId,
            String clientId,
            String purpose,
            ConsentStatus status,
            Instant validFrom,
            Instant validUntil,
            AcquisitionChannel acquisitionChannel,
            CaptureMethod captureMethod,
            String sourceSystem,
            String externalConsentId,
            EvidencePolicy evidencePolicy,
            EvidenceStatus evidenceStatus,
            boolean trustedSourceAuthorization,
            String authorizationReference,
            Instant capturedAt,
            String capturedBy,
            String captureLocation,
            String importBatchReference,
            UUID currentRevisionId,
            Integer currentRevisionNo,
            UUID decisionId,
            UUID evidenceBundleId,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy,
            long version
    ) {
        validateValidity(validFrom, validUntil);
        return new Consent(id, tenantId, externalRequestId, consentType, subjectId, clientId, purpose, status,
                validFrom, validUntil, acquisitionChannel, captureMethod, sourceSystem, externalConsentId,
                evidencePolicy, evidenceStatus, trustedSourceAuthorization, authorizationReference,
                capturedAt, capturedBy, captureLocation, importBatchReference,
                currentRevisionId, currentRevisionNo, decisionId, evidenceBundleId,
                createdAt, updatedAt, createdBy, updatedBy, version);
    }

    public void linkContentRevision(UUID revisionId, int revisionNo, String actorId, Instant now) {
        ensureState(ConsentStatus.REGISTERED, ConsentStatus.REGISTERED);
        Objects.requireNonNull(revisionId, "revisionId must not be null");
        if (revisionNo <= 0 || (currentRevisionNo != null && revisionNo <= currentRevisionNo)) {
            throw new IllegalArgumentException("revisionNo must be greater than current revision");
        }
        actorId = requireText(actorId, "actorId");
        this.currentRevisionId = revisionId;
        this.currentRevisionNo = revisionNo;
        this.decisionId = null;
        this.authorizationReference = null;
        this.evidenceBundleId = null;
        this.evidenceStatus = evidencePolicy == EvidencePolicy.REQUIRED
                ? EvidenceStatus.PENDING : EvidenceStatus.NOT_REQUIRED;
        touch(actorId, now);
        pendingEvents.add(new ConsentContentRevisionLinkedEvent(
                UUID.randomUUID(), tenantId, id, revisionId, revisionNo, actorId, now));
    }

    public void requestAuthorization(String reasonCode, String actorId, Instant now) {
        requireContentRevision();
        ensureState(ConsentStatus.REGISTERED, ConsentStatus.AWAITING_AUTHORIZATION);
        reasonCode = defaultText(reasonCode, "AUTHORIZATION_REQUESTED");
        actorId = requireText(actorId, "actorId");
        transitionTo(ConsentStatus.AWAITING_AUTHORIZATION, reasonCode, null, actorId, now);

        pendingEvents.add(new ConsentAuthorizationRequestedEvent(
                UUID.randomUUID(), tenantId, id, actorId, reasonCode, now));
    }

    public void recordDecision(UUID newDecisionId, UUID revisionId, String outcome, String actorId, Instant now) {
        ensureState(ConsentStatus.AWAITING_AUTHORIZATION, ConsentStatus.AWAITING_AUTHORIZATION);
        requireContentRevision();

        if (!currentRevisionId.equals(Objects.requireNonNull(revisionId, "revisionId must not be null"))) {
            throw new IllegalArgumentException("Decision must reference the current content revision");
        }

        this.decisionId = Objects.requireNonNull(newDecisionId, "decisionId must not be null");
        actorId = requireText(actorId, "actorId");
        touch(actorId, now);

        pendingEvents.add(new ConsentDecisionCapturedEvent(
                UUID.randomUUID(), tenantId, id, newDecisionId, revisionId,
                requireText(outcome, "outcome"), actorId, now));
    }

    public void assertCanAuthorize() {
        ensureState(ConsentStatus.AWAITING_AUTHORIZATION, ConsentStatus.AUTHORIZED);
        requireContentRevision();
        if (evidencePolicy == EvidencePolicy.REQUIRED && evidenceStatus != EvidenceStatus.VERIFIED) {
            throw new EvidenceNotVerifiedException(id, evidenceStatus);
        }
    }

    public void assertCanReject() {
        ensureState(ConsentStatus.AWAITING_AUTHORIZATION, ConsentStatus.REJECTED);
        requireContentRevision();
    }

    /** INV-02: AUTHORIZED can only be reached from AWAITING_AUTHORIZATION. */
    public void authorize(String authorizationReference, String actorId, Instant now) {
        assertCanAuthorize();
        requireDecision();
        authorizationReference = requireText(authorizationReference, "authorizationReference", 300);
        actorId = requireText(actorId, "actorId");
        this.authorizationReference = authorizationReference;
        transitionTo(ConsentStatus.AUTHORIZED, "CONSENT_AUTHORIZED", null, actorId, now);
        pendingEvents.add(new ConsentAuthorizedEvent(
                UUID.randomUUID(), tenantId, id, actorId, authorizationReference, now));
    }

    public void reject(String reasonCode, String reasonDetail, String actorId, Instant now) {
        assertCanReject();
        requireDecision();
        reasonCode = requireText(reasonCode, "reasonCode");
        actorId = requireText(actorId, "actorId");
        transitionTo(ConsentStatus.REJECTED, reasonCode, trimToNull(reasonDetail), actorId, now);
        pendingEvents.add(new ConsentRejectedEvent(
                UUID.randomUUID(), tenantId, id, actorId, reasonCode, trimToNull(reasonDetail), now));
    }

    /**
     * @return true when the subject was bound by this call
     */
    public boolean bindSubject(String subjectRef, String actorId, Instant now) {
        String requested = trimToNull(subjectRef, "subjectRef", 200);

        if (requested == null) {
            return false;
        }

        if (subjectId != null) {
            if (!subjectId.equals(requested)) {
                throw new IllegalArgumentException("subjectRef does not match the subject already bound to the consent");
            }
            return false;
        }

        if (status != ConsentStatus.REGISTERED && status != ConsentStatus.AWAITING_AUTHORIZATION) {
            throw new IllegalStateException("subject can only be bound before the decision, current status " + status);
        }

        this.subjectId = requested;
        touch(actorId, now);
        return true;
    }

    public void linkEvidenceBundle(
            UUID bundleId, UUID revisionId, EvidenceStatus status, String actorId, Instant now) {
        ensureMutableEvidenceState();
        requireContentRevision();
        if (!currentRevisionId.equals(Objects.requireNonNull(revisionId, "revisionId must not be null"))) {
            throw new IllegalArgumentException("Evidence bundle must reference current content revision");
        }
        this.evidenceBundleId = Objects.requireNonNull(bundleId, "bundleId must not be null");
        updateEvidenceStatusInternal(status, actorId, now);
    }

    public void updateEvidenceStatus(
            UUID bundleId, EvidenceStatus status, String actorId, Instant now) {
        ensureMutableEvidenceState();
        if (evidenceBundleId == null || !evidenceBundleId.equals(bundleId)) {
            throw new IllegalArgumentException("Evidence bundle does not match consent reference");
        }
        updateEvidenceStatusInternal(status, actorId, now);
    }

    private void updateEvidenceStatusInternal(EvidenceStatus status, String actorId, Instant now) {
        status = Objects.requireNonNull(status, "evidenceStatus must not be null");
        if (evidencePolicy == EvidencePolicy.NOT_REQUIRED && status != EvidenceStatus.NOT_REQUIRED) {
            throw new IllegalArgumentException("Evidence status cannot be changed when evidence is NOT_REQUIRED");
        }
        this.evidenceStatus = status;
        actorId = requireText(actorId, "actorId");
        touch(actorId, now);
        pendingEvents.add(new ConsentEvidenceStatusChangedEvent(
                UUID.randomUUID(), tenantId, id, evidenceBundleId, status, actorId, now));
    }

    public void suspend(String reasonCode, String reasonDetail, String actorId, Instant now) {
        ensureState(ConsentStatus.AUTHORIZED, ConsentStatus.SUSPENDED);
        reasonCode = requireText(reasonCode, "reasonCode");
        actorId = requireText(actorId, "actorId");
        transitionTo(ConsentStatus.SUSPENDED, reasonCode, trimToNull(reasonDetail), actorId, now);
        pendingEvents.add(new ConsentSuspendedEvent(
                UUID.randomUUID(), tenantId, id, actorId, reasonCode, trimToNull(reasonDetail), now));
    }

    public void revoke(String reasonCode, String reasonDetail, String actorId, Instant now) {
        if (status != ConsentStatus.AUTHORIZED && status != ConsentStatus.SUSPENDED) {
            throw new InvalidConsentStateTransitionException(id, status, ConsentStatus.REVOKED);
        }
        reasonCode = requireText(reasonCode, "reasonCode");
        actorId = requireText(actorId, "actorId");
        transitionTo(ConsentStatus.REVOKED, reasonCode, trimToNull(reasonDetail), actorId, now);
        pendingEvents.add(new ConsentRevokedEvent(
                UUID.randomUUID(), tenantId, id, actorId, reasonCode, trimToNull(reasonDetail), now));
    }

    public void expire(String actorId, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (validUntil.isAfter(now)) {
            throw new ConsentNotYetExpiredException(id, validUntil, now);
        }
        if (status != ConsentStatus.REGISTERED
                && status != ConsentStatus.AWAITING_AUTHORIZATION
                && status != ConsentStatus.AUTHORIZED
                && status != ConsentStatus.SUSPENDED) {
            throw new InvalidConsentStateTransitionException(id, status, ConsentStatus.EXPIRED);
        }
        actorId = requireText(actorId, "actorId");
        transitionTo(ConsentStatus.EXPIRED, "CONSENT_EXPIRED", null, actorId, now);
        pendingEvents.add(new ConsentExpiredEvent(UUID.randomUUID(), tenantId, id, actorId, now));
    }

    public void cancel(String reasonCode, String reasonDetail, String actorId, Instant now) {
        if (status != ConsentStatus.REGISTERED && status != ConsentStatus.AWAITING_AUTHORIZATION) {
            throw new InvalidConsentStateTransitionException(id, status, ConsentStatus.CANCELLED);
        }
        reasonCode = requireText(reasonCode, "reasonCode");
        actorId = requireText(actorId, "actorId");
        transitionTo(ConsentStatus.CANCELLED, reasonCode, trimToNull(reasonDetail), actorId, now);
        pendingEvents.add(new ConsentCancelledEvent(
                UUID.randomUUID(), tenantId, id, actorId, reasonCode, trimToNull(reasonDetail), now));
    }

    private void transitionTo(
            ConsentStatus target, String reasonCode, String reasonDetail, String actorId, Instant now) {
        now = Objects.requireNonNull(now, "now must not be null");
        ConsentStatus previous = status;
        status = target;
        touch(actorId, now);
        pendingTransitions.add(new ConsentTransition(
                UUID.randomUUID(), id, tenantId, previous, target, reasonCode, reasonDetail, now));
    }

    private void touch(String actorId, Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedBy = requireText(actorId, "actorId");
    }

    private void ensureState(ConsentStatus required, ConsentStatus target) {
        if (status != required) {
            throw new InvalidConsentStateTransitionException(id, status, target);
        }
    }

    private void ensureMutableEvidenceState() {
        if (status == ConsentStatus.REVOKED || status == ConsentStatus.EXPIRED
                || status == ConsentStatus.REJECTED || status == ConsentStatus.CANCELLED) {
            throw new InvalidConsentStateTransitionException(id, status, status);
        }
    }

    private void requireContentRevision() {
        if (currentRevisionId == null || currentRevisionNo == null) {
            throw new ConsentContentNotFinalizedException(id);
        }
    }

    private void requireDecision() {
        if (decisionId == null) {
            throw new DecisionCaptureRequiredException(id);
        }
    }

    private void validateReferencePairs() {
        if ((currentRevisionId == null) != (currentRevisionNo == null)) {
            throw new IllegalArgumentException("current revision id/no must be both null or both present");
        }
    }

    private static void validateValidity(Instant validFrom, Instant validUntil) {
        if (validFrom == null || validUntil == null) {
            throw new InvalidConsentValidityException("validFrom and validUntil are required");
        }
        if (!validUntil.isAfter(validFrom)) {
            throw new InvalidConsentValidityException("validUntil must be after validFrom");
        }
    }

    private static String requireText(String value, String field) {
        String result = trimToNull(value);
        if (result == null) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }

    private static String requireText(String value, String field, int maxLength) {
        String result = trimToNull(value, field, maxLength);
        if (result == null) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }

    private static String trimToNull(String value, String field, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static String defaultText(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public String externalRequestId() { return externalRequestId; }
    public String consentType() { return consentType; }
    public String subjectId() { return subjectId; }
    public String clientId() { return clientId; }
    public String purpose() { return purpose; }
    public ConsentStatus status() { return status; }
    public Instant validFrom() { return validFrom; }
    public Instant validUntil() { return validUntil; }
    public AcquisitionChannel acquisitionChannel() { return acquisitionChannel; }
    public CaptureMethod captureMethod() { return captureMethod; }
    public String sourceSystem() { return sourceSystem; }
    public String externalConsentId() { return externalConsentId; }
    public EvidencePolicy evidencePolicy() { return evidencePolicy; }
    public EvidenceStatus evidenceStatus() { return evidenceStatus; }
    public boolean trustedSourceAuthorization() { return trustedSourceAuthorization; }
    public String authorizationReference() { return authorizationReference; }
    public Instant capturedAt() { return capturedAt; }
    public String capturedBy() { return capturedBy; }
    public String captureLocation() { return captureLocation; }
    public String importBatchReference() { return importBatchReference; }
    public UUID currentRevisionId() { return currentRevisionId; }
    public Integer currentRevisionNo() { return currentRevisionNo; }
    public UUID decisionId() { return decisionId; }
    public UUID evidenceBundleId() { return evidenceBundleId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public long version() { return version; }
    public boolean terminal() {
        return status == ConsentStatus.REJECTED || status == ConsentStatus.REVOKED
                || status == ConsentStatus.EXPIRED || status == ConsentStatus.CANCELLED;
    }

    public List<DomainEvent> pendingEvents() { return List.copyOf(pendingEvents); }
    public List<ConsentTransition> pendingTransitions() { return List.copyOf(pendingTransitions); }
    public void clearPendingChanges() {
        pendingEvents.clear();
        pendingTransitions.clear();
    }
}

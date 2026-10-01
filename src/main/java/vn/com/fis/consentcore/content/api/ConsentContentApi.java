package vn.com.fis.consentcore.content.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConsentContentApi {
    ContentRevisionResult createInitialFinalized(
            String tenantId, UUID consentId, ContentRevisionInput input, String actorId, Instant now);

    ContentRevisionResult createNextFinalized(
            String tenantId, UUID consentId, ContentRevisionInput input, String actorId, Instant now);

    ContentRevisionResult get(String tenantId, UUID consentId, UUID revisionId);

    ContentRevisionResult getCurrent(String tenantId, UUID consentId);

    List<ContentRevisionResult> list(String tenantId, UUID consentId);
}

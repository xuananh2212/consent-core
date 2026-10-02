package vn.com.fis.consentcore.auth;

import vn.com.fis.consentcore.shared.api.ActorType;

record AdminUser(
        String id,
        String username,
        String passwordHash,
        String displayName,
        String tenantId,
        String actorId,
        ActorType actorType,
        boolean enabled
) {
}

package vn.com.fis.consentcore.registry.adapter.in.rest;

import java.time.Instant;
import java.util.Map;

record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String correlationId,
        Map<String, Object> details
) {
}

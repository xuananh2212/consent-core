package vn.com.fis.consentcore.audit.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.audit.api.AuditQueryApi;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;

@RestController
@RequestMapping("/api/v1/admin/audit")
class AuditAdminController {
    private final AuditQueryApi auditQueryApi;
    private final RequestContextFactory contextFactory;

    AuditAdminController(AuditQueryApi auditQueryApi, RequestContextFactory contextFactory) {
        this.auditQueryApi = auditQueryApi;
        this.contextFactory = contextFactory;
    }

    @GetMapping
    AuditQueryApi.AuditPage search(
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) UUID aggregateId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        return auditQueryApi.search(new AuditQueryApi.AuditCriteria(
                contextFactory.tenantId(request), aggregateType, aggregateId, action, actorId,
                correlationId, occurredFrom, occurredTo, page, size));
    }
}

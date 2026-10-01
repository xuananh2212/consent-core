package vn.com.fis.consentcore.operations.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.operations.api.OutboxOperationsApi;

@RestController
@RequestMapping("/api/v1/admin/outbox")
class OutboxAdminController {
    private final OutboxOperationsApi operationsApi;
    private final RequestContextFactory contextFactory;

    OutboxAdminController(OutboxOperationsApi operationsApi, RequestContextFactory contextFactory) {
        this.operationsApi = operationsApi;
        this.contextFactory = contextFactory;
    }

    @GetMapping("/summary")
    Map<String, Long> summary(HttpServletRequest request) {
        return operationsApi.summary(contextFactory.tenantId(request));
    }

    @PostMapping("/{eventId}/requeue")
    Map<String, Object> requeue(@PathVariable UUID eventId, HttpServletRequest request) {
        return Map.of("eventId", eventId,
                "requeued", operationsApi.requeue(eventId, contextFactory.create(request)));
    }
}

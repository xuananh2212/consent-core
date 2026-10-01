package vn.com.fis.consentcore.registry.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.registry.api.result.ConsentDecisionResult;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentDecisionsUseCase;

@RestController
@RequestMapping("/api/v1/consents/{consentId}/decisions")
class ConsentDecisionController {
    private final GetConsentDecisionsUseCase getDecisionsUseCase;
    private final RequestContextFactory contextFactory;

    ConsentDecisionController(
            GetConsentDecisionsUseCase getDecisionsUseCase, RequestContextFactory contextFactory) {
        this.getDecisionsUseCase = getDecisionsUseCase;
        this.contextFactory = contextFactory;
    }

    @GetMapping
    List<ConsentDecisionResult> decisions(@PathVariable UUID consentId, HttpServletRequest request) {
        return getDecisionsUseCase.getDecisions(contextFactory.tenantId(request), consentId);
    }
}

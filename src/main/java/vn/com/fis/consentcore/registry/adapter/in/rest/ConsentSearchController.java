package vn.com.fis.consentcore.registry.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.registry.api.query.ConsentSearchCriteria;
import vn.com.fis.consentcore.registry.api.query.PageResult;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;
import vn.com.fis.consentcore.registry.api.usecase.SearchConsentsUseCase;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;

@RestController
@RequestMapping("/api/v1/consents")
class ConsentSearchController {
    private final SearchConsentsUseCase searchUseCase;
    private final RequestContextFactory contextFactory;

    ConsentSearchController(SearchConsentsUseCase searchUseCase, RequestContextFactory contextFactory) {
        this.searchUseCase = searchUseCase;
        this.contextFactory = contextFactory;
    }

    @GetMapping
    PageResult<ConsentResult> search(
            HttpServletRequest request,
            @RequestParam(required = false) ConsentStatus status,
            @RequestParam(required = false) String consentType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String externalConsentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchUseCase.search(new ConsentSearchCriteria(
                contextFactory.tenantId(request), status, consentType, subjectId, clientId, sourceSystem,
                externalConsentId, createdFrom, createdTo, page, size));
    }
}

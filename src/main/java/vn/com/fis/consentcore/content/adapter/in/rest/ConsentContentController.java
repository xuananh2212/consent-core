package vn.com.fis.consentcore.content.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.content.api.ConsentContentApi;
import vn.com.fis.consentcore.content.api.ContentRevisionResult;

@RestController
@RequestMapping("/api/v1/consents/{consentId}/revisions")
public class ConsentContentController {
    private final ConsentContentApi contentApi;
    private final RequestContextFactory contextFactory;

    public ConsentContentController(ConsentContentApi contentApi, RequestContextFactory contextFactory) {
        this.contentApi = contentApi;
        this.contextFactory = contextFactory;
    }

    @GetMapping
    public List<ContentRevisionResult> list(@PathVariable UUID consentId, HttpServletRequest request) {
        return contentApi.list(contextFactory.tenantId(request), consentId);
    }

    @GetMapping("/current")
    public ContentRevisionResult current(@PathVariable UUID consentId, HttpServletRequest request) {
        return contentApi.getCurrent(contextFactory.tenantId(request), consentId);
    }

    @GetMapping("/{revisionId}")
    public ContentRevisionResult get(
            @PathVariable UUID consentId, @PathVariable UUID revisionId, HttpServletRequest request) {
        return contentApi.get(contextFactory.tenantId(request), consentId, revisionId);
    }
}

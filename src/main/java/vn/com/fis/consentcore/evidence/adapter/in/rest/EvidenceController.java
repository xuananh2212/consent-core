package vn.com.fis.consentcore.evidence.adapter.in.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.evidence.api.EvidenceArtifactResult;
import vn.com.fis.consentcore.evidence.api.EvidenceBundleResult;
import vn.com.fis.consentcore.evidence.api.EvidenceDownload;
import vn.com.fis.consentcore.evidence.api.EvidenceManagementApi;
import vn.com.fis.consentcore.evidence.api.EvidenceUploadSessionResult;

@RestController
@RequestMapping("/api/v1/consents/{consentId}/evidence")
public class EvidenceController {
    private final EvidenceManagementApi evidenceApi;
    private final RequestContextFactory contextFactory;
    private final ObjectMapper objectMapper;

    public EvidenceController(
            EvidenceManagementApi evidenceApi,
            RequestContextFactory contextFactory,
            ObjectMapper objectMapper) {
        this.evidenceApi = evidenceApi;
        this.contextFactory = contextFactory;
        this.objectMapper = objectMapper;
    }


    @PostMapping("/upload-sessions")
    public EvidenceUploadSessionResult createUploadSession(
            @PathVariable UUID consentId,
            @Valid @RequestBody UploadSessionRequest body,
            HttpServletRequest request) {
        return evidenceApi.createUploadSession(consentId, body.evidenceType(), body.originalFilename(),
                body.mediaType(), body.declaredSize(), body.metadata(), body.retentionUntil(),
                contextFactory.create(request));
    }

    @PostMapping(value = "/upload-sessions/{sessionId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvidenceArtifactResult uploadSessionContent(
            @PathVariable UUID consentId,
            @PathVariable UUID sessionId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Evidence file must not be empty");
        return evidenceApi.uploadSessionContent(consentId, sessionId, file.getInputStream(),
                contextFactory.create(request));
    }

    @DeleteMapping("/upload-sessions/{sessionId}")
    public EvidenceUploadSessionResult cancelUploadSession(
            @PathVariable UUID consentId,
            @PathVariable UUID sessionId,
            HttpServletRequest request) {
        return evidenceApi.cancelUploadSession(consentId, sessionId, contextFactory.create(request));
    }

    @PostMapping(value = "/artifacts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvidenceArtifactResult upload(
            @PathVariable UUID consentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam String evidenceType,
            @RequestParam(required = false) UUID supersedesArtifactId,
            @RequestParam(required = false) Instant retentionUntil,
            @RequestParam(defaultValue = "{}") String metadata,
            HttpServletRequest request) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Evidence file must not be empty");
        }
        return evidenceApi.upload(
                consentId, evidenceType, file.getOriginalFilename(),
                file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                file.getSize(), file.getInputStream(), supersedesArtifactId,
                readMap(metadata), retentionUntil, contextFactory.create(request));
    }

    @PostMapping("/bundles/{bundleId}/verification")
    public EvidenceBundleResult verify(
            @PathVariable UUID consentId,
            @PathVariable UUID bundleId,
            @Valid @RequestBody VerificationRequest body,
            HttpServletRequest request) {
        return evidenceApi.verify(
                consentId, bundleId, body.artifactId(), body.result(), body.verificationMethod(),
                body.reasonCode(), body.reasonDetail(), body.verificationData(),
                contextFactory.create(request));
    }

    @PostMapping("/bundles/{bundleId}/legal-hold")
    public EvidenceBundleResult legalHold(
            @PathVariable UUID consentId,
            @PathVariable UUID bundleId,
            @Valid @RequestBody LegalHoldRequest body,
            HttpServletRequest request) {
        return evidenceApi.setLegalHold(
                consentId, bundleId, body.legalHold(), contextFactory.create(request));
    }

    @GetMapping("/bundle")
    public EvidenceBundleResult bundle(@PathVariable UUID consentId, HttpServletRequest request) {
        return evidenceApi.getBundle(contextFactory.tenantId(request), consentId);
    }

    @GetMapping("/artifacts")
    public List<EvidenceArtifactResult> artifacts(
            @PathVariable UUID consentId, HttpServletRequest request) {
        return evidenceApi.listArtifacts(contextFactory.tenantId(request), consentId);
    }

    @GetMapping("/artifacts/{artifactId}/content")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable UUID consentId,
            @PathVariable UUID artifactId,
            HttpServletRequest request) throws IOException {
        EvidenceDownload result = evidenceApi.download(
                consentId, artifactId, contextFactory.create(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.mediaType()))
                .contentLength(result.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(result.originalFilename()).build().toString())
                .body(new InputStreamResource(result.content().open()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("metadata must be a JSON object", exception);
        }
    }


    public record UploadSessionRequest(
            @NotBlank String evidenceType,
            @NotBlank String originalFilename,
            @NotBlank String mediaType,
            long declaredSize,
            @NotNull Map<String, Object> metadata,
            Instant retentionUntil) { }

    public record VerificationRequest(
            UUID artifactId,
            @NotBlank String result,
            @NotBlank String verificationMethod,
            String reasonCode,
            String reasonDetail,
            @NotNull Map<String, Object> verificationData) {
    }

    public record LegalHoldRequest(boolean legalHold) {
    }
}

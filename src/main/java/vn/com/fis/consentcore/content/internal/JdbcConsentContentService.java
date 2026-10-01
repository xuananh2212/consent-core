package vn.com.fis.consentcore.content.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.content.api.ConsentContentApi;
import vn.com.fis.consentcore.content.api.ContentConstraint;
import vn.com.fis.consentcore.content.api.ContentObligation;
import vn.com.fis.consentcore.content.api.ContentPermission;
import vn.com.fis.consentcore.content.api.ContentResource;
import vn.com.fis.consentcore.content.api.ContentRevisionInput;
import vn.com.fis.consentcore.content.api.ContentRevisionResult;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
public class JdbcConsentContentService implements ConsentContentApi {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final boolean enforcePermissionCatalog;

    public JdbcConsentContentService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${consent.reference.enforce-permission-catalog:false}")
            boolean enforcePermissionCatalog
    ) {
        this.jdbc = jdbc;
        this.enforcePermissionCatalog = enforcePermissionCatalog;
        this.objectMapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override
    @Transactional
    public ContentRevisionResult createInitialFinalized(
            String tenantId,
            UUID consentId,
            ContentRevisionInput input,
            String actorId,
            Instant now
    ) {
        Integer count = jdbc.queryForObject(
                """
                select count(*)
                  from consent_revision
                 where tenant_id = ?
                   and consent_id = ?
                """,
                Integer.class,
                requireText(tenantId, "tenantId"),
                UuidUtils.toBytes(Objects.requireNonNull(consentId))
        );

        if (count != null && count > 0) {
            throw new ContentValidationException(
                    "Initial revision already exists for consent " + consentId
            );
        }

        return create(
                tenantId,
                consentId,
                1,
                input,
                actorId,
                now,
                false
        );
    }

    @Override
    @Transactional
    public ContentRevisionResult createNextFinalized(
            String tenantId,
            UUID consentId,
            ContentRevisionInput input,
            String actorId,
            Instant now
    ) {
        Integer current = jdbc.queryForObject(
                """
                select coalesce(max(revision_no), 0)
                  from consent_revision
                 where tenant_id = ?
                   and consent_id = ?
                """,
                Integer.class,
                requireText(tenantId, "tenantId"),
                UuidUtils.toBytes(Objects.requireNonNull(consentId))
        );

        int next = (current == null ? 0 : current) + 1;

        if (next == 1) {
            throw new ContentValidationException(
                    "Initial revision must be created through consent registration"
            );
        }

        return create(
                tenantId,
                consentId,
                next,
                input,
                actorId,
                now,
                true
        );
    }

    private ContentRevisionResult create(
            String tenantId,
            UUID consentId,
            int revisionNo,
            ContentRevisionInput input,
            String actorId,
            Instant now,
            boolean supersedeCurrent
    ) {
        tenantId = requireText(tenantId, "tenantId");
        validate(tenantId, input);

        actorId = requireText(actorId, "actorId");

        Objects.requireNonNull(now, "now must not be null");
        Timestamp sqlNow = toTimestamp(now);

        UUID revisionId = UUID.randomUUID();
        String snapshot = json(input.presentationSnapshot());
        String hash = hash(input);

        try {
            if (supersedeCurrent) {
                jdbc.update(
                        """
                        update consent_revision
                           set status = 'SUPERSEDED',
                               superseded_at = ?,
                               superseded_by = ?
                         where tenant_id = ?
                           and consent_id = ?
                           and status = 'FINALIZED'
                        """,
                        sqlNow,
                        actorId,
                        tenantId,
                        UuidUtils.toBytes(consentId)
                );
            }

            jdbc.update(
                    """
                    insert into consent_revision(
                        id,
                        tenant_id,
                        consent_id,
                        revision_no,
                        purpose_code,
                        presentation_snapshot,
                        content_hash,
                        status,
                        created_at,
                        created_by
                    )
                    values (
                        ?, ?, ?, ?, ?,
                        ?, ?, 'DRAFT', ?, ?
                    )
                    """,
                    UuidUtils.toBytes(revisionId),
                    tenantId,
                    UuidUtils.toBytes(consentId),
                    revisionNo,
                    requireText(input.purposeCode(), "purposeCode"),
                    snapshot,
                    hash,
                    sqlNow,
                    actorId
            );

            int order = 0;

            for (ContentPermission permission : input.permissions()) {
                jdbc.update(
                        """
                        insert into consent_permission(
                            id,
                            tenant_id,
                            revision_id,
                            permission_code,
                            attributes,
                            sort_order
                        )
                        values (?, ?, ?, ?, ?, ?)
                        """,
                        UuidUtils.toBytes(UUID.randomUUID()),
                        tenantId,
                        UuidUtils.toBytes(revisionId),
                        requireText(
                                permission.permissionCode(),
                                "permissionCode"
                        ),
                        json(permission.attributes()),
                        order++
                );
            }

            order = 0;

            for (ContentResource resource : input.resources()) {
                jdbc.update(
                        """
                        insert into consent_resource(
                            id,
                            tenant_id,
                            revision_id,
                            resource_type,
                            resource_id,
                            attributes,
                            sort_order
                        )
                        values (?, ?, ?, ?, ?, ?, ?)
                        """,
                        UuidUtils.toBytes(UUID.randomUUID()),
                        tenantId,
                        UuidUtils.toBytes(revisionId),
                        requireText(
                                resource.resourceType(),
                                "resourceType"
                        ),
                        requireText(
                                resource.resourceId(),
                                "resourceId"
                        ),
                        json(resource.attributes()),
                        order++
                );
            }

            order = 0;

            for (ContentConstraint constraint : input.constraints()) {
                jdbc.update(
                        """
                        insert into consent_constraint(
                            id,
                            tenant_id,
                            revision_id,
                            constraint_type,
                            operator,
                            constraint_value,
                            sort_order
                        )
                        values (?, ?, ?, ?, ?, ?, ?)
                        """,
                        UuidUtils.toBytes(UUID.randomUUID()),
                        tenantId,
                        UuidUtils.toBytes(revisionId),
                        requireText(
                                constraint.constraintType(),
                                "constraintType"
                        ),
                        requireText(
                                constraint.operator(),
                                "operator"
                        ),
                        json(constraint.value()),
                        order++
                );
            }

            order = 0;

            for (ContentObligation obligation : input.obligations()) {
                jdbc.update(
                        """
                        insert into consent_obligation(
                            id,
                            tenant_id,
                            revision_id,
                            obligation_type,
                            parameters,
                            sort_order
                        )
                        values (?, ?, ?, ?, ?, ?)
                        """,
                        UuidUtils.toBytes(UUID.randomUUID()),
                        tenantId,
                        UuidUtils.toBytes(revisionId),
                        requireText(
                                obligation.obligationType(),
                                "obligationType"
                        ),
                        json(obligation.parameters()),
                        order++
                );
            }

            jdbc.update(
                    """
                    update consent_revision
                       set status = 'FINALIZED',
                           finalized_at = ?,
                           finalized_by = ?
                     where id = ?
                       and tenant_id = ?
                       and status = 'DRAFT'
                    """,
                    sqlNow,
                    actorId,
                    UuidUtils.toBytes(revisionId),
                    tenantId
            );

        } catch (DuplicateKeyException duplicate) {
            throw new ContentValidationException(
                    "Duplicate permission/resource or identical content revision"
            );
        }

        return get(tenantId, consentId, revisionId);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentRevisionResult get(
            String tenantId,
            UUID consentId,
            UUID revisionId
    ) {
        List<ContentRevisionResult> rows = jdbc.query(
                """
                select id,
                       tenant_id,
                       consent_id,
                       revision_no,
                       purpose_code,
                       presentation_snapshot,
                       content_hash,
                       status,
                       created_at,
                       created_by,
                       finalized_at,
                       finalized_by
                  from consent_revision
                 where tenant_id = ?
                   and consent_id = ?
                   and id = ?
                """,
                (rs, rowNum) -> mapRevision(rs),
                requireText(tenantId, "tenantId"),
                UuidUtils.toBytes(consentId),
                UuidUtils.toBytes(revisionId)
        );

        if (rows.isEmpty()) {
            throw new ContentRevisionNotFoundException(revisionId);
        }

        return withChildren(rows.getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public ContentRevisionResult getCurrent(
            String tenantId,
            UUID consentId
    ) {
        List<UUID> ids = jdbc.query(
                """
                select current_revision_id
                  from consent
                 where tenant_id = ?
                   and id = ?
                   and current_revision_id is not null
                """,
                (rs, rowNum) -> UuidUtils.fromBytes(rs.getBytes(1)),
                requireText(tenantId, "tenantId"),
                UuidUtils.toBytes(consentId)
        );

        if (ids.isEmpty()) {
            throw new ContentValidationException(
                    "Consent does not have a current content revision"
            );
        }

        return get(tenantId, consentId, ids.getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentRevisionResult> list(
            String tenantId,
            UUID consentId
    ) {
        return jdbc.query(
                """
                select id,
                       tenant_id,
                       consent_id,
                       revision_no,
                       purpose_code,
                       presentation_snapshot,
                       content_hash,
                       status,
                       created_at,
                       created_by,
                       finalized_at,
                       finalized_by
                  from consent_revision
                 where tenant_id = ?
                   and consent_id = ?
                 order by revision_no desc
                """,
                (rs, rowNum) -> withChildren(mapRevision(rs)),
                requireText(tenantId, "tenantId"),
                UuidUtils.toBytes(consentId)
        );
    }

    private ContentRevisionResult mapRevision(ResultSet rs)
            throws SQLException {

        Timestamp finalizedAt = rs.getTimestamp("finalized_at");

        return new ContentRevisionResult(
                UuidUtils.fromBytes(rs.getBytes("id")),
                rs.getString("tenant_id"),
                UuidUtils.fromBytes(rs.getBytes("consent_id")),
                rs.getInt("revision_no"),
                rs.getString("purpose_code"),
                rs.getString("content_hash"),
                rs.getString("status"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                readMap(rs.getString("presentation_snapshot")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by"),
                finalizedAt == null ? null : finalizedAt.toInstant(),
                rs.getString("finalized_by")
        );
    }

    private ContentRevisionResult withChildren(
            ContentRevisionResult base
    ) {
        byte[] revisionId = UuidUtils.toBytes( Objects.requireNonNull(base.id(), "revisionId") );

        List<ContentPermission> permissions = jdbc.query(
                """
                select permission_code,
                       attributes
                  from consent_permission
                 where tenant_id = ?
                   and revision_id = ?
                 order by sort_order,
                          permission_code
                """,
                (rs, n) -> new ContentPermission(
                        rs.getString(1),
                        readMap(rs.getString(2))
                ),
                base.tenantId(),
                revisionId
        );

        List<ContentResource> resources = jdbc.query(
                """
                select resource_type,
                       resource_id,
                       attributes
                  from consent_resource
                 where tenant_id = ?
                   and revision_id = ?
                 order by sort_order,
                          resource_type,
                          resource_id
                """,
                (rs, n) -> new ContentResource(
                        rs.getString(1),
                        rs.getString(2),
                        readMap(rs.getString(3))
                ),
                base.tenantId(),
                revisionId
        );

        List<ContentConstraint> constraints = jdbc.query(
                """
                select constraint_type,
                       operator,
                       constraint_value
                  from consent_constraint
                 where tenant_id = ?
                   and revision_id = ?
                 order by sort_order,
                          constraint_type
                """,
                (rs, n) -> new ContentConstraint(
                        rs.getString(1),
                        rs.getString(2),
                        readValue(rs.getString(3))
                ),
                base.tenantId(),
                revisionId
        );

        List<ContentObligation> obligations = jdbc.query(
                """
                select obligation_type,
                       parameters
                  from consent_obligation
                 where tenant_id = ?
                   and revision_id = ?
                 order by sort_order,
                          obligation_type
                """,
                (rs, n) -> new ContentObligation(
                        rs.getString(1),
                        readMap(rs.getString(2))
                ),
                base.tenantId(),
                revisionId
        );

        return new ContentRevisionResult(
                base.id(),
                base.tenantId(),
                base.consentId(),
                base.revisionNo(),
                base.purposeCode(),
                base.contentHash(),
                base.status(),
                permissions,
                resources,
                constraints,
                obligations,
                base.presentationSnapshot(),
                base.createdAt(),
                base.createdBy(),
                base.finalizedAt(),
                base.finalizedBy()
        );
    }

    private void validate(
            String tenantId,
            ContentRevisionInput input
    ) {
        Objects.requireNonNull(
                input,
                "content input must not be null"
        );

        requireText(input.purposeCode(), "purposeCode");

        if (input.permissions().isEmpty()) {
            throw new ContentValidationException(
                    "At least one structured permission is required"
            );
        }

        if (input.permissions().size() > 200
                || input.resources().size() > 500
                || input.constraints().size() > 200
                || input.obligations().size() > 200) {

            throw new ContentValidationException(
                    "Consent content exceeds configured collection limits"
            );
        }

        if (enforcePermissionCatalog) {
            for (ContentPermission permission : input.permissions()) {
                Integer count = jdbc.queryForObject(
                        """
                        select count(*)
                          from permission_catalog
                         where tenant_id = ?
                           and permission_code = ?
                           and active = 1
                        """,
                        Integer.class,
                        tenantId,
                        requireText(
                                permission.permissionCode(),
                                "permissionCode"
                        )
                );

                if (count == null || count != 1) {
                    throw new ContentValidationException(
                            "Permission is not active in tenant catalog: "
                                    + permission.permissionCode()
                    );
                }
            }
        }
    }

    private String hash(ContentRevisionInput input) {
        Map<String, Object> canonical = new LinkedHashMap<>();

        canonical.put("constraints", input.constraints());
        canonical.put("obligations", input.obligations());
        canonical.put("permissions", input.permissions());
        canonical.put(
                "presentationSnapshot",
                input.presentationSnapshot()
        );
        canonical.put("purposeCode", input.purposeCode());
        canonical.put("resources", input.resources());

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] serialized =
                    objectMapper.writeValueAsBytes(canonical);

            return HexFormat.of().formatHex(
                    digest.digest(serialized)
            );

        } catch (NoSuchAlgorithmException
                 | JsonProcessingException exception) {

            throw new IllegalStateException(
                    "Cannot calculate consent content hash",
                    exception
            );
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null ? Map.of() : value
            );
        } catch (JsonProcessingException exception) {
            throw new ContentValidationException(
                    "Content contains a value that cannot be serialized as JSON"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try {
            return json == null
                    ? Map.of()
                    : objectMapper.readValue(json, Map.class);

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Invalid JSON stored in consent content",
                    exception
            );
        }
    }

    private Object readValue(String json) {
        try {
            return json == null
                    ? null
                    : objectMapper.readValue(json, Object.class);

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Invalid JSON stored in consent constraint",
                    exception
            );
        }
    }

    private static String requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new ContentValidationException(
                    field + " must not be blank"
            );
        }

        return value.trim();
    }
}
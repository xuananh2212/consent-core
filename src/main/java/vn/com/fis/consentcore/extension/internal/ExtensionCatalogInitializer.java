package vn.com.fis.consentcore.extension.internal;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.extension.api.ConsentExtension;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.helper.UuidUtils.toBytes;
import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Component
public class ExtensionCatalogInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final List<ConsentExtension> extensions;

    public ExtensionCatalogInitializer(
            JdbcTemplate jdbc,
            Clock clock,
            List<ConsentExtension> extensions
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.extensions = extensions;
    }

    @Override
    public void run(ApplicationArguments args) {
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);

        for (ConsentExtension extension : extensions) {
            UUID id = UUID.randomUUID();

            jdbc.update("""
                    MERGE INTO extension_registration target
                USING (
                    SELECT
                        ? AS id,
                        ? AS extension_name,
                        ? AS description,
                        ? AS created_at,
                        ? AS updated_at
                    FROM dual
                ) source
                ON (
                    target.extension_name = source.extension_name
                )
                WHEN MATCHED THEN
                    UPDATE SET
                        target.active = 1,
                        target.updated_at = source.updated_at,
                        target.updated_by = 'SYSTEM'
                WHEN NOT MATCHED THEN
                    INSERT (
                        id,
                        extension_name,
                        description,
                        implementation_type,
                        active,
                        created_at,
                        created_by,
                        updated_at,
                        updated_by,
                        configuration_schema,
                        schema_version
                    )
                    VALUES (
                        source.id,
                        source.extension_name,
                        source.description,
                        'SPRING_BEAN',
                        1,
                        source.created_at,
                        'SYSTEM',
                        source.updated_at,
                        'SYSTEM',
                        '{}',
                        1
                    )
                    """,
                    UuidUtils.toBytes(id),
                    extension.name(),
                    "Spring bean consent extension",
                    now,
                    now
            );
        }
    }
}
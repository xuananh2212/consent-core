package vn.com.fis.consentcore.extension.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.com.fis.consentcore.extension.api.ConsentExtension;
import vn.com.fis.consentcore.extension.api.ExtensionContext;
import vn.com.fis.consentcore.extension.api.ExtensionEngine;
import vn.com.fis.consentcore.extension.api.ExtensionPoint;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
public class JdbcExtensionEngine implements ExtensionEngine {
    private static final Logger log = LoggerFactory.getLogger(JdbcExtensionEngine.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;
    private final Map<String, ConsentExtension> extensions;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public JdbcExtensionEngine(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            Clock clock,
            List<ConsentExtension> extensionBeans,
            @Value("${consent.extensions.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.enabled = enabled;
        Map<String, ConsentExtension> byName = new HashMap<>();
        for (ConsentExtension extension : extensionBeans) {
            if (byName.put(extension.name(), extension) != null) {
                throw new IllegalStateException("Duplicate extension name: " + extension.name());
            }
        }
        this.extensions = Map.copyOf(byName);
    }

    @Override
    public void execute(ExtensionPoint point, ExtensionContext context) {
        if (!enabled) return;
        List<Binding> bindings = findBindings(point, context);
        for (Binding binding : bindings) {
            ConsentExtension extension = extensions.get(binding.extensionName());
            if (extension == null) {
                logExecution(binding, point, context, 0, "SKIPPED", 0, "No Spring bean registered");
                if (binding.critical()) {
                    throw new ExtensionExecutionException(
                            "Critical extension is not available: " + binding.extensionName(), null);
                }
                continue;
            }
            executeBinding(extension, binding, point, context);
        }
    }

    private void executeBinding(
            ConsentExtension extension, Binding binding, ExtensionPoint point, ExtensionContext context) {
        Throwable lastError = null;
        for (int attempt = 1; attempt <= binding.maxAttempts(); attempt++) {
            long started = System.nanoTime();
            Future<?> future = executor.submit(() -> {
                try {
                    extension.execute(point, context.withConfiguration(binding.configuration()));
                } catch (Exception exception) {
                    throw new ExtensionTaskException(exception);
                }
            });
            try {
                future.get(binding.timeoutMs(), TimeUnit.MILLISECONDS);
                long duration = Duration.ofNanos(System.nanoTime() - started).toMillis();
                logExecution(binding, point, context, attempt, "SUCCESS", duration, null);
                return;
            } catch (TimeoutException timeout) {
                future.cancel(true);
                lastError = timeout;
                logExecution(binding, point, context, attempt, "TIMEOUT",
                        Duration.ofNanos(System.nanoTime() - started).toMillis(), "Execution timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                lastError = interrupted;
                logExecution(binding, point, context, attempt, "FAILURE",
                        Duration.ofNanos(System.nanoTime() - started).toMillis(), "Thread interrupted");
                break;
            } catch (ExecutionException failed) {
                lastError = failed.getCause() instanceof ExtensionTaskException wrapper
                        ? wrapper.getCause() : failed.getCause();
                logExecution(binding, point, context, attempt, "FAILURE",
                        Duration.ofNanos(System.nanoTime() - started).toMillis(), safeMessage(lastError));
            }
        }
        if (binding.critical()) {
            throw new ExtensionExecutionException(
                    "Critical extension failed: " + binding.extensionName() + " at " + point, lastError);
        }
        log.warn("Non-critical extension {} failed at {}", binding.extensionName(), point, lastError);
    }

    private List<Binding> findBindings(ExtensionPoint point, ExtensionContext context) {
        return jdbc.query("""
                select b.id, b.extension_name, b.critical, b.timeout_ms, b.max_attempts,
                       b.configuration
                  from extension_binding b
                  join extension_registration r on r.extension_name = b.extension_name
                 where b.tenant_id = ? and b.extension_point = ?
                   and b.active = 1 and r.active = 1
                   and (b.consent_type is null or b.consent_type = ?)
                   and (b.client_id is null or b.client_id = ?)
                   and (b.acquisition_channel is null or b.acquisition_channel = ?)
                   and (b.capture_method is null or b.capture_method = ?)
                   and (b.evidence_type is null or b.evidence_type = ?)
                 order by b.execution_order, b.id
                """, (rs, n) -> new Binding(
                        UuidUtils.fromBytes(rs.getBytes("id")), rs.getString("extension_name"),
                        rs.getBoolean("critical"), rs.getInt("timeout_ms"),
                        rs.getInt("max_attempts"), readMap(rs.getString("configuration"))),
                context.tenantId(), point.name(), context.consentType(), context.clientId(),
                context.acquisitionChannel() == null ? null : context.acquisitionChannel().name(),
                context.captureMethod() == null ? null : context.captureMethod().name(),
                context.evidenceType());
    }

    private void logExecution(
            Binding binding,
            ExtensionPoint point,
            ExtensionContext context,
            int attempt,
            String result,
            long durationMs,
            String errorMessage) {
        String correlation = context.commandContext() == null
                ? "extension-" + UUID.randomUUID()
                : context.commandContext().correlationId();
        jdbc.update("""
                insert into extension_execution_log(
                    id, tenant_id, binding_id, consent_id, extension_point,
                    attempt, result, duration_ms, error_message, correlation_id, executed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UuidUtils.toBytes(UUID.randomUUID()), context.tenantId(),
                UuidUtils.toBytes(binding.id()), UuidUtils.toBytes(context.consentId()), point.name(),
                Math.max(attempt, 0), result, durationMs, truncate(errorMessage, 2000),
                correlation, toTimestamp(clock.instant()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid extension configuration JSON", exception);
        }
    }

    private static String safeMessage(Throwable error) {
        return error == null ? null : error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    private static String truncate(String value, int length) {
        if (value == null || value.length() <= length) return value;
        return value.substring(0, length);
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private record Binding(
            UUID id,
            String extensionName,
            boolean critical,
            int timeoutMs,
            int maxAttempts,
            Map<String, Object> configuration) {
    }

    private static final class ExtensionTaskException extends RuntimeException {
        private ExtensionTaskException(Throwable cause) {
            super(cause);
        }
    }
}

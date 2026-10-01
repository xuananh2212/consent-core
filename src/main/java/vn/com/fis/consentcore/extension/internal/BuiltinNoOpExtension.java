package vn.com.fis.consentcore.extension.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.extension.api.ConsentExtension;
import vn.com.fis.consentcore.extension.api.ExtensionContext;
import vn.com.fis.consentcore.extension.api.ExtensionPoint;

@Component
public class BuiltinNoOpExtension implements ConsentExtension {
    private static final Logger log = LoggerFactory.getLogger(BuiltinNoOpExtension.class);

    @Override
    public String name() {
        return "builtin-noop";
    }

    @Override
    public void execute(ExtensionPoint point, ExtensionContext context) {
        log.debug("Executed {} for tenant={} consent={}", point, context.tenantId(), context.consentId());
    }
}

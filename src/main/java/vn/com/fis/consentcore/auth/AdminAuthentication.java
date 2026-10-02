package vn.com.fis.consentcore.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import vn.com.fis.consentcore.shared.api.ActorType;

/** Người gọi đã chứng minh bằng access token do chính Consent Core ký. */
public record AdminAuthentication(
        String username,
        String tenantId,
        String actorId,
        ActorType actorType
) implements Authentication {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) {
        // Token hợp lệ thì phiên đã được xác thực.
    }

    @Override
    public String getName() {
        return username;
    }
}

package vn.com.fis.consentcore.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Có Bearer thì kiểm tra access token của Consent Core.
 * Không có Bearer thì để request đi tiếp và Core vẫn đọc header như cũ.
 */
@Component
public class AdminAccessTokenFilter extends OncePerRequestFilter {
    private final AdminTokenService tokens;

    AdminAccessTokenFilter(AdminTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            AdminAuthentication authentication = tokens.authenticate(header.substring(7).trim());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (AuthRejectedException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"" + ex.getMessage() + "\"}");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

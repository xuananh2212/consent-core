package vn.com.fis.consentcore.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "consent.security.mode", havingValue = "jwt")
class JwtSecurityConfiguration {
    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health/**", "/actuator/info", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
    };

    @Bean
    SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/internal/v1/**").hasAnyAuthority(
                                "SCOPE_consent.flow.read", "SCOPE_consent.flow.write", "SCOPE_consent.admin")
                        .requestMatchers("/internal/v1/**").hasAnyAuthority(
                                "SCOPE_consent.flow.write", "SCOPE_consent.admin")
                        .requestMatchers("/api/v1/admin/**").hasAuthority("SCOPE_consent.admin")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyAuthority(
                                "SCOPE_consent.read", "SCOPE_consent.write", "SCOPE_consent.admin")
                        .requestMatchers("/api/v1/**").hasAnyAuthority("SCOPE_consent.write", "SCOPE_consent.admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }))
                .build();
    }
}

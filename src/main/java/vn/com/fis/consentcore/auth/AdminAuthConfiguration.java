package vn.com.fis.consentcore.auth;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.com.fis.consentcore.shared.api.ActorType;

@Configuration
@EnableConfigurationProperties(AdminAuthProperties.class)
class AdminAuthConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthConfiguration.class);

    @Bean
    PasswordEncoder adminPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    ApplicationRunner seedAdminUser(
            AdminUserRepository users,
            PasswordEncoder adminPasswordEncoder,
            AdminAuthProperties properties,
            Clock clock) {
        return (ApplicationArguments args) -> {
            if (!users.tableExists("ADMIN_USER")) {
                users.createTables();
            }
            if (users.findByUsername(properties.seedUsername()).isEmpty()) {
                users.insertUser(new AdminUser(
                        UUID.randomUUID().toString(),
                        properties.seedUsername(),
                        adminPasswordEncoder.encode(properties.seedPassword()),
                        "Quản trị viên",
                        "open-banking",
                        "admin",
                        ActorType.USER,
                        true), clock.instant());
                log.info("Seeded admin user {}", properties.seedUsername());
            }
        };
    }
}

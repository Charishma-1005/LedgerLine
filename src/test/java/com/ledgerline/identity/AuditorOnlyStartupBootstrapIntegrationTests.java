package com.ledgerline.identity;

import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.application.RegistrationService;
import com.ledgerline.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuditorOnlyStartupBootstrapIntegrationTests.SeedUserConfiguration.class)
class AuditorOnlyStartupBootstrapIntegrationTests {
    private static final String DATABASE_NAME =
            "ledgerline_auditor_startup_" + UUID.randomUUID().toString().replace("-", "");
    private static final String CANDIDATE_EMAIL =
            "auditor-startup-" + UUID.randomUUID() + "@example.com";
    private static final String PASSWORD = "Startup-Auditor-Password-927!";

    @Autowired private IdentityUserStore users;

    @DynamicPropertySource
    static void bootstrapProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:" + DATABASE_NAME
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        properties.add("ledgerline.admin.bootstrap-email", () -> "");
        properties.add("ledgerline.auditor.bootstrap-email", () -> CANDIDATE_EMAIL);
    }

    @Test
    void applicationStartupBootstrapsAuditorWithoutAdminConfiguration() {
        assertEquals(UserRole.AUDITOR,
                users.findByNormalizedEmail(CANDIDATE_EMAIL).orElseThrow().role());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SeedUserConfiguration {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ApplicationRunner seedAuditorCandidate(RegistrationService registration,
                                               IdentityUserStore users) {
            return new ApplicationRunner() {
                @Override
                public void run(ApplicationArguments args) {
                    if (!users.existsByNormalizedEmail(CANDIDATE_EMAIL)) {
                        registration.register(CANDIDATE_EMAIL, PASSWORD);
                    }
                }
            };
        }
    }
}

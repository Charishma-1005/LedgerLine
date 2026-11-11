package com.ledgerline.identity.infrastructure;

import com.ledgerline.identity.application.AdminBootstrapService;
import com.ledgerline.identity.application.AuditorBootstrapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final System.Logger LOGGER = System.getLogger(AdminBootstrapRunner.class.getName());
    private final AdminBootstrapService bootstrap;
    private final AuditorBootstrapService auditorBootstrap;
    private final String configuredEmail;
    private final String configuredAuditorEmail;

    public AdminBootstrapRunner(AdminBootstrapService bootstrap,
                                AuditorBootstrapService auditorBootstrap,
                                @Value("${ledgerline.admin.bootstrap-email:}") String configuredEmail,
                                @Value("${ledgerline.auditor.bootstrap-email:}") String configuredAuditorEmail) {
        this.bootstrap = bootstrap;
        this.auditorBootstrap = auditorBootstrap;
        this.configuredEmail = configuredEmail;
        this.configuredAuditorEmail = configuredAuditorEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!configuredEmail.isBlank()) {
            if (bootstrap.promoteConfiguredUser(configuredEmail)) {
                LOGGER.log(System.Logger.Level.INFO, "Initial LedgerLine administrator was bootstrapped.");
            } else {
                LOGGER.log(System.Logger.Level.INFO, "Administrator bootstrap did not change any account.");
            }
        }
        if (!configuredAuditorEmail.isBlank()) {
            if (auditorBootstrap.promoteConfiguredAuditor(configuredAuditorEmail)) {
                LOGGER.log(System.Logger.Level.INFO, "Initial LedgerLine auditor was bootstrapped.");
            } else {
                LOGGER.log(System.Logger.Level.INFO, "Auditor bootstrap did not change any account.");
            }
        }
    }
}

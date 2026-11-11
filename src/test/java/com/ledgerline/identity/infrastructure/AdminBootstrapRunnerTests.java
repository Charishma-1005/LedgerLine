package com.ledgerline.identity.infrastructure;

import com.ledgerline.identity.application.AdminBootstrapService;
import com.ledgerline.identity.application.AuditorBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapRunnerTests {
    @Test
    void auditorBootstrapRunsWhenAdminBootstrapIsNotConfigured() {
        AdminBootstrapService admins = mock(AdminBootstrapService.class);
        AuditorBootstrapService auditors = mock(AuditorBootstrapService.class);
        when(auditors.promoteConfiguredAuditor("auditor@example.com")).thenReturn(true);

        new AdminBootstrapRunner(admins, auditors, "", "auditor@example.com")
                .run(mock(ApplicationArguments.class));

        verify(admins, never()).promoteConfiguredUser(org.mockito.ArgumentMatchers.anyString());
        verify(auditors).promoteConfiguredAuditor("auditor@example.com");
    }

    @Test
    void adminBootstrapStillRunsWhenAuditorBootstrapIsNotConfigured() {
        AdminBootstrapService admins = mock(AdminBootstrapService.class);
        AuditorBootstrapService auditors = mock(AuditorBootstrapService.class);
        when(admins.promoteConfiguredUser("admin@example.com")).thenReturn(true);

        new AdminBootstrapRunner(admins, auditors, "admin@example.com", "")
                .run(mock(ApplicationArguments.class));

        verify(admins).promoteConfiguredUser("admin@example.com");
        verify(auditors, never()).promoteConfiguredAuditor(org.mockito.ArgumentMatchers.anyString());
    }
}

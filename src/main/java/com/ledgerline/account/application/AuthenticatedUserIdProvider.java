package com.ledgerline.account.application;

import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.domain.EmailAddress;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserIdProvider {
    private final IdentityUserStore users;

    public AuthenticatedUserIdProvider(IdentityUserStore users) {
        this.users = users;
    }

    public long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication is required");
        }

        String normalizedEmail = EmailAddress.normalizeForLookup(authentication.getName());
        var user = users.findByNormalizedEmail(normalizedEmail)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is unavailable"));
        if (!user.enabled() || user.passwordHash() == null) {
            throw new AccessDeniedException("Authenticated user is disabled");
        }
        return user.id();
    }
}

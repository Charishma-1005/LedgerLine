package com.ledgerline.identity.infrastructure;

import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.domain.EmailAddress;
import com.ledgerline.identity.domain.IdentityUser;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final IdentityUserStore users;

    public DatabaseUserDetailsService(IdentityUserStore users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        final String normalizedEmail;
        try {
            normalizedEmail = EmailAddress.normalizeForLookup(email);
        } catch (RuntimeException exception) {
            throw new UsernameNotFoundException("Invalid email or password");
        }

        IdentityUser user = users.findByNormalizedEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        String storedHash = user.passwordHash() == null ? "" : user.passwordHash();
        return User.withUsername(user.normalizedEmail())
                .password(storedHash)
                .authorities(switch (user.role()) {
                    case ADMIN -> new SimpleGrantedAuthority[]{new SimpleGrantedAuthority("ROLE_USER"),
                            new SimpleGrantedAuthority("ROLE_ADMIN")};
                    case AUDITOR -> new SimpleGrantedAuthority[]{new SimpleGrantedAuthority("ROLE_AUDITOR")};
                    case USER -> new SimpleGrantedAuthority[]{new SimpleGrantedAuthority("ROLE_USER")};
                })
                .disabled(!user.enabled() || user.passwordHash() == null)
                .build();
    }
}

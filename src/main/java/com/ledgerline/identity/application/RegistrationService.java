package com.ledgerline.identity.application;

import com.ledgerline.identity.domain.EmailAddress;
import com.ledgerline.identity.domain.IdentityUser;
import com.ledgerline.identity.domain.PasswordPolicy;
import com.ledgerline.audit.application.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final IdentityUserStore users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public RegistrationService(IdentityUserStore users, PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional
    public IdentityUser register(String emailInput, String rawPassword) {
        EmailAddress email = EmailAddress.of(emailInput);
        if (!PasswordPolicy.isValid(rawPassword)) {
            throw new IllegalArgumentException("Password does not meet the required length policy");
        }
        // Hash before the existence check so duplicate-address responses do not skip BCrypt work.
        String passwordHash = passwordEncoder.encode(rawPassword);
        if (users.existsByNormalizedEmail(email.normalized())) {
            throw new DuplicateEmailException();
        }

        IdentityUser created = users.saveNewUser(email.value(), email.normalized(), passwordHash);
        audit.recordRegistration(created.id());
        return created;
    }
}

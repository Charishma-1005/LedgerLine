package com.ledgerline.identity;

import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.domain.IdentityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityAuthenticationIntegrationTests {
    private static final String PASSWORD = "A-Longer-Password-928!";

    private String email;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityUserStore users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUniqueTestEmail() {
        email = "phase2-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registrationStoresOnlyBcryptHash() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        IdentityUser user = users.findByNormalizedEmail(email).orElseThrow();
        assertNotEquals(PASSWORD, user.passwordHash());
        assertTrue(user.passwordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches(PASSWORD, user.passwordHash()));
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception {
        register(email);

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", email.toUpperCase())
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Unable to create an account with those details.")));
    }

    @Test
    void invalidPasswordIsRejectedWithoutCreatingAnAccount() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("at least 12 characters")));

        assertTrue(users.findByNormalizedEmail(email).isEmpty());
    }

    @Test
    void loginRequiresValidCredentialsAndRoleUserCanReachAccountThenLogout() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "incorrect-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        register(email);

        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email.toUpperCase())
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/account").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(email)));

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/account").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginFailureMessageDoesNotDistinguishUnknownAndKnownEmails() throws Exception {
        MvcResult unknown = mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection()).andReturn();
        register(email);
        MvcResult known = mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertTrue(unknown.getResponse().getRedirectedUrl().equals(known.getResponse().getRedirectedUrl()));
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid email or password.")));
    }

    @Test
    void invalidCsrfTokenIsRejectedForRegistration() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf().useInvalidToken())
                        .param("email", email).param("password", PASSWORD))
                .andExpect(status().isForbidden());
        assertTrue(users.findByNormalizedEmail(email).isEmpty());
    }

    @Test
    void registrationAndProtectedRoutesEnforceCsrfAndAuthentication() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(post("/register")
                        .param("email", "csrf-user@example.com")
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "limited@example.com", roles = "AUDITOR")
    void accountRouteRejectsAuthenticatedUserWithoutRoleUser() throws Exception {
        mockMvc.perform(get("/account"))
                .andExpect(status().isForbidden());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection());
    }
}

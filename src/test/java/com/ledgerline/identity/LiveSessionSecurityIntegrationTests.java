package com.ledgerline.identity;

import com.ledgerline.identity.application.AdminBootstrapService;
import com.ledgerline.identity.application.AdminManagementService;
import com.ledgerline.identity.application.IdentityUserStore;
import com.ledgerline.identity.application.RegistrationService;
import com.ledgerline.identity.domain.IdentityUser;
import com.ledgerline.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=true")
@ActiveProfiles("test")
class LiveSessionSecurityIntegrationTests {
    private static final String PASSWORD = "Live-Session-Check-928!";
    private static final Pattern CSRF_INPUT = Pattern.compile("name=\\\"_csrf\\\" value=\\\"([^\\\"]+)\\\"");

    @LocalServerPort
    private int port;

    @Autowired private RegistrationService registration;
    @Autowired private IdentityUserStore users;
    @Autowired private AdminBootstrapService bootstrap;
    @Autowired private AdminManagementService admins;
    @Autowired private SessionRegistry sessions;
    @Autowired private FilterChainProxy filterChain;

    @Test
    void realLoginRotatesSessionAndSessionCookieHasSecurityAttributes() throws Exception {
        String email = uniqueEmail("live-user");
        registration.register(email, PASSWORD);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        HttpResponse<String> loginPage = get(client, "/login", null);
        String oldSession = sessionId(loginPage);
        String initialCookie = cookiePair(loginPage);
        String csrf = csrfToken(loginPage.body());
        String secureCookie = sessionCookieHeader(loginPage);
        assertTrue(secureCookie.toLowerCase().contains("httponly"), secureCookie);
        assertTrue(secureCookie.toLowerCase().contains("samesite=lax"), secureCookie);
        assertTrue(secureCookie.toLowerCase().contains("secure"), secureCookie);

        HttpResponse<String> login = post(client, "/login", initialCookie,
                form("email", email, "password", PASSWORD, "_csrf", csrf));
        assertEquals(302, login.statusCode());
        String newSession = sessionId(login);
        assertNotEquals(oldSession, newSession, "Servlet container must rotate the session ID on authentication");

        HttpResponse<String> account = get(client, "/account", cookiePair(login));
        assertEquals(200, account.statusCode());
    }

    @Test
    void disablingUserExpiresTheirLiveAuthenticatedSession() throws Exception {
        var filterNames = filterChain.getFilterChains().getFirst().getFilters().stream()
                .map(filter -> filter.getClass().getSimpleName()).toList();
        assertTrue(filterNames.contains("ConcurrentSessionFilter"), filterNames.toString());
        Object concurrentFilter = filterChain.getFilterChains().getFirst().getFilters().stream()
                .filter(filter -> filter.getClass().getSimpleName().equals("ConcurrentSessionFilter"))
                .findFirst().orElseThrow();
        assertTrue(ReflectionTestUtils.getField(concurrentFilter, "sessionRegistry") == sessions,
                "ConcurrentSessionFilter and admin service must share the same SessionRegistry instance");
        IdentityUser disabledUser = registerAndFind(uniqueEmail("live-disabled"));
        IdentityUser admin = ensureAdmin();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        String liveCookie = login(client, disabledUser.normalizedEmail());
        assertEquals(200, get(client, "/account", liveCookie).statusCode());
        Object principal = sessions.getAllPrincipals().stream().filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .filter(candidate -> candidate.getUsername().equals(disabledUser.normalizedEmail()))
                .findFirst().orElseThrow(() -> new AssertionError("Live servlet authentication is missing from SessionRegistry"));
        String sessionId = liveCookie.substring("JSESSIONID=".length());
        assertTrue(sessions.getAllSessions(principal, false).stream().anyMatch(info -> info.getSessionId().equals(sessionId)),
                "SessionRegistry must track the rotated session ID");

        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                admin.normalizedEmail(), "", List.of(() -> "ROLE_USER", () -> "ROLE_ADMIN")));
        try {
            admins.setUserEnabled(disabledUser.id(), false);
        } finally {
            SecurityContextHolder.clearContext();
        }

        var expiredSession = sessions.getAllSessions(principal, true).stream()
                .filter(info -> info.getSessionId().equals(sessionId)).findFirst()
                .orElseThrow(() -> new AssertionError("Disabled user's live session disappeared from SessionRegistry"));
        assertTrue(expiredSession.isExpired(), "Admin disable must expire the tracked session");

        HttpResponse<String> protectedResponse = get(client, "/account", liveCookie);
        assertEquals(302, protectedResponse.statusCode());
        assertTrue(protectedResponse.headers().firstValue("location").orElse("").contains("expired"));
    }

    private IdentityUser registerAndFind(String email) {
        registration.register(email, PASSWORD);
        return users.findByNormalizedEmail(email).orElseThrow();
    }

    private IdentityUser ensureAdmin() {
        return users.findAllUsers().stream()
                .filter(user -> user.enabled() && user.role() == UserRole.ADMIN)
                .findFirst()
                .orElseGet(() -> {
                    String email = uniqueEmail("live-admin");
                    registration.register(email, PASSWORD);
                    if (!bootstrap.promoteConfiguredUser(email)) {
                        throw new IllegalStateException("Unable to bootstrap test administrator");
                    }
                    return users.findByNormalizedEmail(email).orElseThrow();
                });
    }

    private String login(HttpClient client, String email) throws Exception {
        HttpResponse<String> page = get(client, "/login", null);
        HttpResponse<String> response = post(client, "/login", cookiePair(page),
                form("email", email, "password", PASSWORD, "_csrf", csrfToken(page.body())));
        assertEquals(302, response.statusCode());
        return cookiePair(response);
    }

    private HttpResponse<String> get(HttpClient client, String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String cookie, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path))
                        .header("Cookie", cookie)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String csrfToken(String html) {
        Matcher matcher = CSRF_INPUT.matcher(html);
        if (!matcher.find()) throw new AssertionError("Login page did not render a CSRF token");
        return matcher.group(1);
    }

    private String sessionId(HttpResponse<?> response) {
        Matcher matcher = Pattern.compile("JSESSIONID=([^;]+)").matcher(sessionCookieHeader(response));
        if (!matcher.find()) throw new AssertionError("Response did not set a JSESSIONID cookie");
        return matcher.group(1);
    }

    private String cookiePair(HttpResponse<?> response) {
        String header = sessionCookieHeader(response);
        int end = header.indexOf(';');
        if (end < 0) throw new AssertionError("JSESSIONID cookie has no attributes");
        return header.substring(0, end);
    }

    private String sessionCookieHeader(HttpResponse<?> response) {
        return response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Response did not set a JSESSIONID cookie"));
    }

    private String form(String... values) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < values.length; i += 2) {
            if (!body.isEmpty()) body.append('&');
            body.append(encode(values[i])).append('=').append(encode(values[i + 1]));
        }
        return body.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}

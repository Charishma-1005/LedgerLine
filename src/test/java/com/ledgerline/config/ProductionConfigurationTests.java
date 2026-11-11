package com.ledgerline.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductionConfigurationTests {

    @Test
    void applicationDoesNotDefaultToDevelopmentAndDevelopmentFallbacksStayProfileScoped() throws IOException {
        Properties base = load("application.properties");
        Properties dev = load("application-dev.properties");

        assertNull(base.getProperty("spring.profiles.default"));
        assertNull(base.getProperty("spring.profiles.active"));
        assertEquals("${DB_URL}", base.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME}", base.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", base.getProperty("spring.datasource.password"));
        assertEquals("${DB_URL:jdbc:postgresql://localhost:5432/ledgerline}",
                dev.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME:postgres}", dev.getProperty("spring.datasource.username"));
    }

    @Test
    void productionProfileRequiresExplicitDatasourceVariablesAndSecureCookieAttributes() throws IOException {
        Properties prod = load("application-prod.properties");

        assertEquals("${DB_URL}", prod.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME}", prod.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", prod.getProperty("spring.datasource.password"));
        assertEquals("true", prod.getProperty("server.servlet.session.cookie.secure"));
        assertEquals("true", prod.getProperty("server.servlet.session.cookie.http-only"));
        assertEquals("lax", prod.getProperty("server.servlet.session.cookie.same-site"));
        assertEquals("verify-full",
                prod.getProperty("spring.datasource.hikari.data-source-properties.sslmode"));
    }

    private Properties load(String resource) throws IOException {
        if (resource.equals("application.properties")) {
            Enumeration<URL> candidates = getClass().getClassLoader().getResources(resource);
            while (candidates.hasMoreElements()) {
                Properties candidate = read(candidates.nextElement());
                if ("ledgerline".equals(candidate.getProperty("spring.application.name"))) return candidate;
            }
            throw new IOException("Missing main application configuration: " + resource);
        }
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing configuration resource: " + resource);
            properties.load(input);
        }
        return properties;
    }

    private Properties read(URL resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = resource.openStream()) {
            properties.load(input);
        }
        return properties;
    }
}

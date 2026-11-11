package com.ledgerline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/login", "/register", "/error", "/favicon.ico",
                                "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/auditor", "/auditor/**").hasRole("AUDITOR")
                        .requestMatchers("/account").hasRole("USER")
                        .requestMatchers("/accounts", "/accounts/**").hasRole("USER")
                        .requestMatchers("/transactions", "/transactions/**").hasRole("USER")
                        .requestMatchers("/reports", "/reports/**").hasRole("USER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            boolean auditor = authentication.getAuthorities().stream()
                                    .anyMatch(authority -> "ROLE_AUDITOR".equals(authority.getAuthority()));
                            response.sendRedirect(request.getContextPath() + (auditor ? "/auditor" : "/account"));
                        })
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(-1)
                        .expiredUrl("/login?expired")
                        .sessionRegistry(sessionRegistry()))
                // CSRF is intentionally enabled for session-authenticated browser forms.
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    CookieSameSiteSupplier sessionCookieSameSiteSupplier() {
        return CookieSameSiteSupplier.ofLax().whenHasName("JSESSIONID");
    }
}

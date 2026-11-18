package com.example.springjpa.security;

import com.example.springjpa.service.BudgetAccountService;
import com.example.springjpa.service.BudgetMcpTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class BudgetSecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BudgetSecurityConfiguration.class);

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${budget.auth.google.client-id:}") String clientId,
            @Value("${budget.auth.google.client-secret:}") String clientSecret) {
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId(configuredOrPlaceholder(clientId, "not-configured"))
                .clientSecret(configuredOrPlaceholder(clientSecret, "not-configured"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email", "profile")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }

    @Bean
    OAuth2AuthorizationRequestResolver googleAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(builder ->
                builder.additionalParameters(parameters -> parameters.put("prompt", "select_account")));
        return resolver;
    }

    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> googleUserService(
            BudgetAccountService accountService) {
        return authorizeGoogleUser(new OidcUserService(), accountService);
    }

    static OAuth2UserService<OidcUserRequest, OidcUser> authorizeGoogleUser(
            OAuth2UserService<OidcUserRequest, OidcUser> delegate,
            BudgetAccountService accountService) {
        return request -> {
            OidcUser user;
            try {
                user = delegate.loadUser(request);
            } catch (OAuth2AuthenticationException exception) {
                log.warn("Google identity loading failed: oauthErrorCode={}", exception.getError().getErrorCode());
                throw exception;
            }
            String email = user.getEmail();
            String subject = user.getSubject();
            boolean emailVerified = Boolean.TRUE.equals(user.getClaim("email_verified"));
            boolean identityPresent = subject != null && !subject.isBlank()
                    && email != null && !email.isBlank();
            boolean authorized = emailVerified && identityPresent;
            log.info("Google account authorization: account={}, emailVerified={}, identityPresent={}, authorized={}",
                    maskEmail(email), emailVerified, identityPresent, authorized);
            if (!authorized) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("unauthorized_google_account"),
                        "A Google account with a verified email is required.");
            }
            accountService.registerOrUpdateGoogleIdentity(subject, email);
            return user;
        };
    }

    static AuthenticationFailureHandler googleAuthenticationFailureHandler() {
            return (request, response, exception) -> {
                String errorCode = exception instanceof OAuth2AuthenticationException oauthException
                        ? oauthException.getError().getErrorCode() : "authentication_failed";
                boolean unauthorizedAccount = "unauthorized_google_account".equals(errorCode);
                boolean verifierConfigurationError = "missing_signature_verifier".equals(errorCode);
                String category = unauthorizedAccount ? "unverified_google_identity"
                        : verifierConfigurationError ? "oidc_verifier_configuration" : "oauth_or_callback_failure";
                log.warn("Google sign-in failed: category={}, exceptionType={}, oauthErrorCode={}",
                        category,
                        exception.getClass().getSimpleName(), errorCode);
                String error = unauthorizedAccount ? "unauthorized_account"
                        : verifierConfigurationError ? "oidc_configuration" : "sign_in_failed";
                response.sendRedirect("/login.html?error=" + error);
            };
    }

    @Bean
    @Order(1)
    SecurityFilterChain budgetMcpSecurityFilterChain(
            HttpSecurity http, BudgetMcpTokenService tokenService) throws Exception {
        http.securityMatcher(new AntPathRequestMatcher("/mcp"))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context
                        .securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> {
                            response.setHeader("WWW-Authenticate", "Bearer");
                            response.sendError(HttpStatus.UNAUTHORIZED.value());
                        }))
                .addFilterBefore(new BudgetMcpBearerTokenFilter(tokenService),
                        org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain budgetSecurityFilterChain(
            HttpSecurity http,
            OAuth2UserService<OidcUserRequest, OidcUser> googleUserService,
            OAuth2AuthorizationRequestResolver googleAuthorizationRequestResolver,
            @Value("${budget.auth.enabled:true}") boolean authenticationEnabled,
            @Value("${budget.auth.google.client-id:}") String clientId,
            @Value("${budget.auth.google.client-secret:}") String clientSecret) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));

        if (!authenticationEnabled) {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/mcp-token").authenticated()
                    .anyRequest().permitAll());
            return http.build();
        }

        boolean googleConfigured = !clientId.isBlank() && !clientSecret.isBlank();
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login.html", "/login.js", "/styles.css", "/error",
                                "/api/auth/status", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/api/mcp-token").authenticated()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .loginPage("/login.html")
                        .failureHandler(googleAuthenticationFailureHandler())
                        .successHandler((request, response, authentication) -> {
                            Object principal = authentication.getPrincipal();
                            String email = principal instanceof OidcUser user ? user.getEmail() : null;
                            log.info("Google sign-in succeeded: account={}, principalType={}",
                                    maskEmail(email), principal.getClass().getSimpleName());
                            new SavedRequestAwareAuthenticationSuccessHandler()
                                    .onAuthenticationSuccess(request, response, authentication);
                        })
                        .authorizationEndpoint(endpoint ->
                                endpoint.authorizationRequestResolver(googleAuthorizationRequestResolver))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleUserService)))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(204)));

        if (!googleConfigured) {
            http.exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, exception) -> {
                        response.sendRedirect("/login.html?setup=required");
                    }));
        }
        return http.build();
    }

    private static String configuredOrPlaceholder(String value, String placeholder) {
        return value.isBlank() ? placeholder : value.trim();
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<missing>";
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "<invalid>";
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}

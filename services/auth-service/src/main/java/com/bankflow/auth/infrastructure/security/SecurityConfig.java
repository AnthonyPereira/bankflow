package com.bankflow.auth.infrastructure.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.json.JsonMapper;

/** Le service valide lui-même les JWT : la Gateway n'est pas l'unique frontière de sécurité. */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * Routes ouvertes sans JWT, listées une à une : aucun préfixe ne doit englober une route
     * protégée comme {@code /auth/me}.
     */
    private static final RequestMatcher PUBLIC_ENDPOINTS = createPublicEndpointsMatcher();

    @Bean("securityFilterChain")
    SecurityFilterChain createSecurityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint unauthorized =
                (request, response, exception) -> {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    writeProblem(
                            request,
                            response,
                            HttpStatus.UNAUTHORIZED,
                            "Authentification requise ou jeton invalide.");
                };
        AccessDeniedHandler forbidden =
                (request, response, exception) ->
                        writeProblem(request, response, HttpStatus.FORBIDDEN, "Accès refusé.");

        // Sûr : le cookie de refresh est SameSite=Strict et la Gateway refuse les origines
        // étrangères.
        return http.csrf(AbstractHttpConfigurer::disable) // NOSONAR
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests ->
                        requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                                .permitAll()
                                .requestMatchers(PUBLIC_ENDPOINTS)
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.bearerTokenResolver(createBearerTokenResolver())
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(createRoleConverter()))
                                .authenticationEntryPoint(unauthorized)
                                .accessDeniedHandler(forbidden))
                .exceptionHandling(exceptions ->
                        exceptions
                                .authenticationEntryPoint(unauthorized)
                                .accessDeniedHandler(forbidden))
                .build();
    }

    /**
     * Sur une route publique, l'en-tête {@code Authorization} est ignoré : un JWT expiré, que le
     * navigateur envoie justement pour rafraîchir sa session, ne doit pas faire répondre 401 à
     * {@code /auth/refresh} ou {@code /auth/login}.
     */
    private static BearerTokenResolver createBearerTokenResolver() {
        var resolver = new DefaultBearerTokenResolver();
        return request -> PUBLIC_ENDPOINTS.matches(request) ? null : resolver.resolve(request);
    }

    private static RequestMatcher createPublicEndpointsMatcher() {
        var paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher("/actuator/health/**"),
                paths.matcher("/actuator/info"),
                paths.matcher("/v3/api-docs/**"),
                paths.matcher("/swagger-ui/**"),
                paths.matcher("/swagger-ui.html"),
                paths.matcher(HttpMethod.GET, "/.well-known/jwks.json"),
                paths.matcher(HttpMethod.GET, "/auth/demo-accounts"),
                paths.matcher(HttpMethod.POST, "/auth/register"),
                paths.matcher(HttpMethod.POST, "/auth/login"),
                paths.matcher(HttpMethod.POST, "/auth/refresh"),
                paths.matcher(HttpMethod.POST, "/auth/logout"));
    }

    /** Le claim {@code role} contient déjà le préfixe ROLE_ attendu par Spring Security. */
    private static JwtAuthenticationConverter createRoleConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detail)
            throws IOException {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.getWriter().write(JSON.writeValueAsString(body));
    }
}

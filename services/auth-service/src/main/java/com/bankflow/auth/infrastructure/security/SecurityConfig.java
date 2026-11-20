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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/** Le service valide lui-même les JWT : la Gateway n'est pas l'unique frontière de sécurité. */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                                .requestMatchers(
                                        "/actuator/health/**",
                                        "/actuator/info",
                                        "/v3/api-docs/**",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html")
                                .permitAll()
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/.well-known/jwks.json",
                                        "/auth/demo-accounts")
                                .permitAll()
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/auth/register",
                                        "/auth/login",
                                        "/auth/refresh",
                                        "/auth/logout")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter()))
                                .authenticationEntryPoint(unauthorized)
                                .accessDeniedHandler(forbidden))
                .exceptionHandling(exceptions ->
                        exceptions
                                .authenticationEntryPoint(unauthorized)
                                .accessDeniedHandler(forbidden))
                .build();
    }

    /** Le claim {@code role} contient déjà le préfixe ROLE_ attendu par Spring Security. */
    private static JwtAuthenticationConverter roleConverter() {
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

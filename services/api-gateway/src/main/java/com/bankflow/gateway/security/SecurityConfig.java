package com.bankflow.gateway.security;

import com.bankflow.gateway.error.ProblemResponses;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Valide les JWT à l'entrée du système et laisse passer les seules routes publiques. */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static final String[] PUBLIC_AUTH_ROUTES = {
        "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint unauthorized =
                (request, response, exception) -> {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    ProblemResponses.write(
                            request,
                            response,
                            HttpStatus.UNAUTHORIZED,
                            "Authentification requise ou jeton invalide.");
                };
        AccessDeniedHandler forbidden =
                (request, response, exception) ->
                        ProblemResponses.write(
                                request, response, HttpStatus.FORBIDDEN, "Accès refusé.");

        // Sûr : le cookie de refresh est SameSite=Strict et la Gateway refuse les origines
        // étrangères.
        return http.csrf(AbstractHttpConfigurer::disable) // NOSONAR
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests ->
                        requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                                .permitAll()
                                .requestMatchers("/actuator/health/**", "/actuator/info")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ROUTES)
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(Customizer.withDefaults())
                                .authenticationEntryPoint(unauthorized)
                                .accessDeniedHandler(forbidden))
                .exceptionHandling(exceptions ->
                        exceptions
                                .authenticationEntryPoint(unauthorized)
                                .accessDeniedHandler(forbidden))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${bankflow.cors.allowed-origins}") List<String> allowedOrigins) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(
                List.of(
                        HttpHeaders.AUTHORIZATION,
                        HttpHeaders.CONTENT_TYPE,
                        "X-Correlation-Id",
                        "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id", HttpHeaders.RETRY_AFTER));
        // Le refresh token circule dans un cookie HttpOnly
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

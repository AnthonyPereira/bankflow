package com.bankflow.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Retire les en-têtes d'identité envoyés par le client, puis les recrée depuis le JWT validé. Ce
 * filtre s'exécute après la chaîne Spring Security.
 */
@Component
@Order(0)
class IdentityHeadersFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var wrapped = new MutableHeadersRequest(request);
        wrapped.remove(USER_ID_HEADER);
        wrapped.remove(USER_ROLE_HEADER);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            // getSubject est @Nullable : un null ferait échouer le ConcurrentHashMap des en-têtes
            var subject = token.getToken().getSubject();
            if (subject != null) {
                wrapped.set(USER_ID_HEADER, subject);
            }
            var role = token.getToken().getClaimAsString("role");
            if (role != null) {
                wrapped.set(USER_ROLE_HEADER, role);
            }
        }
        chain.doFilter(wrapped, response);
    }
}

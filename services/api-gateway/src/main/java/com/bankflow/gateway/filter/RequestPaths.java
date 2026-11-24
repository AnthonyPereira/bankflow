package com.bankflow.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

/** Outils partagés par les filtres de limitation pour lire le chemin d'une requête. */
final class RequestPaths {

    private static final Pattern PATH_PARAMETERS = Pattern.compile(";[^/]*");
    private static final Pattern REPEATED_SLASHES = Pattern.compile("/{2,}");

    private RequestPaths() {}

    /**
     * Ramène le chemin à la forme que comprendra le service en aval. Sans cela, {@code
     * /api/auth/%6Cogin} aurait son propre compteur alors que l'Auth Service le décode en {@code
     * /auth/login}. {@link RateLimitFilter} passe avant le pare-feu de Spring Security et ne peut
     * donc pas compter sur lui pour écarter les chemins non normalisés. {@link UserRateLimitFilter}
     * passe après, mais utilise la même forme pour que sa clé suive la route réellement servie.
     */
    static String normalizePath(HttpServletRequest request) {
        var uri = Objects.requireNonNullElse(request.getRequestURI(), "/");
        var contextPath = Objects.requireNonNullElse(request.getContextPath(), "");
        var raw = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        // Les paramètres de segment (;x=1) sont retirés avant le décodage, comme le fait Spring MVC
        var withoutParameters = PATH_PARAMETERS.matcher(raw).replaceAll("");
        var decoded = UriUtils.decode(withoutParameters, StandardCharsets.UTF_8);
        var path =
                Objects.requireNonNullElse(
                        StringUtils.cleanPath(REPEATED_SLASHES.matcher(decoded).replaceAll("/")),
                        "/");
        return path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
    }
}

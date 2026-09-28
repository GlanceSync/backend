package com.glancesync.modules.security.filter;

import java.util.UUID;
import com.glancesync.common.dto.ErrorResponse;
import com.glancesync.modules.security.service.JwtService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Guards every /api route except the handful needed to get a token in the first place.
 * Expects "Authorization: Bearer <access token>"; on success the caller's user id is
 * attached to the request for controllers to read.
 */
@ServerFilter("/api/**")
public class JwtAuthenticationFilter {

    public static final String USER_ID_ATTRIBUTE = "glancesync.userId";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/verify-email",
            "/api/auth/resend-verification",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/me");

    private static final Pattern PUBLIC_PICTURE =
            Pattern.compile("^/api/users/[0-9a-fA-F-]{36}/pictures/[0-9a-fA-F-]{36}$");

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @RequestFilter
    @Nullable
    public HttpResponse<?> authenticate(HttpRequest<?> request) {
        if (isPublic(request)) {
            return null;
        }

        UUID userId = bearerToken(request).flatMap(jwtService::parseAccessToken).orElse(null);
        if (userId == null) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .body(ErrorResponse.of("You must be signed in."));
        }

        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        return null;
    }

    public static Optional<String> bearerToken(HttpRequest<?> request) {
        return request.getHeaders()
                .findFirst(HttpHeaders.AUTHORIZATION)
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> header.substring(BEARER_PREFIX.length()).trim())
                .filter(token -> !token.isEmpty());
    }

    private static boolean isPublic(HttpRequest<?> request) {
        String path = request.getPath();
        return request.getMethod() == HttpMethod.OPTIONS
                || PUBLIC_PATHS.contains(path)
                || (request.getMethod() == HttpMethod.GET && PUBLIC_PICTURE.matcher(path).matches());
    }
}

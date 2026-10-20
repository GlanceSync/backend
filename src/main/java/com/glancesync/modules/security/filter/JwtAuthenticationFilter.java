package com.glancesync.modules.security.filter;

import java.util.UUID;
import com.glancesync.common.dto.ErrorResponse;
import com.glancesync.modules.auth.service.TokenService;
import com.glancesync.modules.security.service.JwtService;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.HttpHeaders;
import com.glancesync.web.HttpMethod;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpResponse;
import com.glancesync.web.HttpStatus;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Guards every /api route except the handful needed to get a token in the first place.
 * Expects "Authorization: Bearer <access token>"; on success the caller's user id is
 * attached to the request for controllers to read.
 */
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
            "/api/auth/me",
            "/api/auth/oauth/providers");

    private static final Pattern PUBLIC_PICTURE =
            Pattern.compile("^/api/users/[0-9a-fA-F-]{36}/pictures/[0-9a-fA-F-]{36}$");

    // Starting and finishing "Sign in with <provider>": /api/auth/{provider}[/callback]
    private static final Pattern PUBLIC_OAUTH =
            Pattern.compile("^/api/auth/(42|google|facebook)(/callback)?$");

    private static final String ADMIN_PREFIX = "/api/admin/";

    private final JwtService jwtService;
    private final UserDao userDao;

    public JwtAuthenticationFilter(JwtService jwtService, UserDao userDao) {
        this.jwtService = jwtService;
        this.userDao = userDao;
    }

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

        // Checked on every call, not just at sign-in, so a freeze takes effect immediately
        // instead of when the current access token expires.
        UserDao.Access access = userDao.findAccess(userId).orElse(null);
        if (access == null) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .body(ErrorResponse.of("You must be signed in."));
        }
        if (!access.active()) {
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(TokenService.FROZEN_MESSAGE));
        }
        if (request.getPath().startsWith(ADMIN_PREFIX) && !access.admin()) {
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("Administrators only."));
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
                || (request.getMethod() == HttpMethod.GET && PUBLIC_OAUTH.matcher(path).matches())
                || (request.getMethod() == HttpMethod.GET && PUBLIC_PICTURE.matcher(path).matches());
    }
}

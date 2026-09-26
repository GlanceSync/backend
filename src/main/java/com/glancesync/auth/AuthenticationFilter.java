package com.glancesync.auth;

import com.glancesync.api.Dtos;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

import java.util.Set;

/**
 * Guards every /api route except the handful needed to get a session in the first place.
 * On success the caller's user id is attached to the request for controllers to read.
 */
@ServerFilter("/api/**")
public class AuthenticationFilter {

    public static final String USER_ID_ATTRIBUTE = "glancesync.userId";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            // A session probe, not a protected resource: it answers "who am I", and
            // "nobody" is a normal answer. Returning 401 here would paint an error in
            // the browser console on every visit by a signed-out user.
            "/api/auth/me");

    private final SessionService sessionService;

    public AuthenticationFilter(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @RequestFilter
    @Nullable
    public HttpResponse<?> authenticate(HttpRequest<?> request) {
        // Returning null lets the request continue to the controller.
        if (PUBLIC_PATHS.contains(request.getPath()) || request.getMethod().name().equals("OPTIONS")) {
            return null;
        }

        String token = request.getCookies()
                .findCookie(SessionService.COOKIE_NAME)
                .map(cookie -> cookie.getValue())
                .orElse(null);

        Long userId = sessionService.resolveUserId(token).orElse(null);
        if (userId == null) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED)
                    .body(Dtos.ErrorResponse.of("You must be signed in."));
        }

        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        return null;
    }
}

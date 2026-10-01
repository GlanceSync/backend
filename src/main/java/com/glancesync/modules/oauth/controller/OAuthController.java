package com.glancesync.modules.oauth.controller;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.dto.AuthResponse;
import com.glancesync.modules.auth.service.TokenService;
import com.glancesync.modules.oauth.dto.OAuthUserResponse;
import com.glancesync.modules.oauth.enums.AuthProvider;
import com.glancesync.modules.oauth.service.OAuthUserService;
import com.glancesync.modules.oauth.service.ProviderAuthService;
import com.glancesync.modules.security.service.RefreshTokenCookieService;
import com.glancesync.modules.user.dto.UserResponse;
import com.glancesync.modules.user.entity.User;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.session.Session;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Sign in with 42 / Google / Facebook".
 * <ol>
 *   <li>The app navigates the browser to /api/auth/{provider} (42, google or facebook), which
 *       redirects to the provider.</li>
 *   <li>The provider sends the browser back to the app's /auth/{provider}/callback page.</li>
 *   <li>That page passes code and state to /api/auth/{provider}/callback, which answers like
 *       /api/auth/login: an access token in the body and the refresh cookie.</li>
 * </ol>
 */
@Tag(name = "OAuth")
@Controller("/api/auth")
@ExecuteOn(TaskExecutors.BLOCKING)
public class OAuthController {

    /** Every ProviderAuthService bean, by the provider's key ("42", "google", "facebook"). */
    private final Map<String, ProviderAuthService> services;
    private final OAuthUserService oauthUserService;
    private final TokenService tokenService;
    private final RefreshTokenCookieService refreshCookie;
    private final String appUrl;

    public OAuthController(List<ProviderAuthService> services, OAuthUserService oauthUserService,
                           TokenService tokenService, RefreshTokenCookieService refreshCookie,
                           @Value("${glancesync.app-url:http://localhost:4200}") String appUrl) {
        this.services = services.stream()
                .collect(Collectors.toUnmodifiableMap(service -> service.provider().key(), service -> service));
        this.oauthUserService = oauthUserService;
        this.tokenService = tokenService;
        this.refreshCookie = refreshCookie;
        this.appUrl = appUrl.replaceAll("/+$", "");
    }

    /** The providers the app can offer a "Sign in with" button for. */
    @Get("/oauth/providers")
    public Map<String, List<String>> providers() {
        return Map.of("providers", Arrays.stream(AuthProvider.values())
                .map(AuthProvider::key)
                .filter(key -> services.containsKey(key) && services.get(key).isEnabled())
                .toList());
    }

    @Get("/{provider}")
    public HttpResponse<?> login(@PathVariable String provider, Session session) {
        ProviderAuthService service = services.get(provider);
        if (service == null || !service.isEnabled()) {
            return HttpResponse.seeOther(URI.create(appUrl + "/login?oauth_error=unavailable"));
        }
        return HttpResponse.seeOther(service.getAuthorizationUrl(session));
    }

    @Get("/{provider}/callback")
    public HttpResponse<AuthResponse> callback(@PathVariable String provider, @Nullable Session session,
                                               @QueryValue @Nullable String code, @QueryValue @Nullable String state,
                                               @QueryValue @Nullable String error) {
        ProviderAuthService service = services.get(provider);
        if (service == null || !service.isEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Signing in with that provider is not available.");
        }
        // Usually access_denied: the person cancelled at the provider.
        if (error != null || code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign-in was cancelled.");
        }

        OAuthUserResponse oauthUser = service.authenticate(code, state, session);
        User user = oauthUserService.findOrCreateOAuthUser(service.provider(), oauthUser);

        TokenService.Tokens tokens = tokenService.issue(user);
        AuthResponse body = new AuthResponse(tokens.accessToken(), tokenService.accessTokenExpiresInSeconds(),
                UserResponse.of(tokens.user()));
        return HttpResponse.ok(body).cookie(refreshCookie.build(tokens.refreshToken()));
    }
}

package com.glancesync.modules.auth.controller;

import com.glancesync.common.dto.ErrorResponse;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.dto.AuthResponse;
import com.glancesync.modules.auth.dto.LoginRequest;
import com.glancesync.modules.auth.dto.RegisterRequest;
import com.glancesync.modules.auth.dto.ResendVerificationRequest;
import com.glancesync.modules.auth.dto.SessionResponse;
import com.glancesync.modules.auth.service.AuthService;
import com.glancesync.modules.auth.service.EmailVerificationService;
import com.glancesync.modules.auth.service.TokenService;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.security.service.JwtService;
import com.glancesync.modules.security.service.RefreshTokenCookieService;
import com.glancesync.modules.user.dto.UserResponse;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URI;
import java.util.Map;

@Tag(name = "Auth")
@Controller("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final JwtService jwtService;
    private final RefreshTokenCookieService refreshCookie;
    private final UserDao userDao;
    private final EmailVerificationService emailVerification;

    public AuthController(AuthService authService, TokenService tokenService, JwtService jwtService,
                          RefreshTokenCookieService refreshCookie, UserDao userDao,
                          EmailVerificationService emailVerification) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.jwtService = jwtService;
        this.refreshCookie = refreshCookie;
        this.userDao = userDao;
        this.emailVerification = emailVerification;
    }

    @Post(value = "/register", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<UserResponse> register(@Body RegisterRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        User user = authService.register(body);
        return HttpResponse.created(new UserResponse(user.id(), user.username(), user.avatarUrl()));
    }

    @Get("/verify-email")
    public HttpResponse<?> verifyEmail(@QueryValue @Nullable String token) {
        boolean verified = emailVerification.verify(token).isPresent();
        URI target = URI.create(emailVerification.appUrl() + "/login?verified=" + (verified ? "success" : "invalid"));
        return HttpResponse.seeOther(target).header("Referrer-Policy", "no-referrer");
    }

    @Post(value = "/resend-verification", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<?> resendVerification(@Body @Nullable ResendVerificationRequest body) {
        emailVerification.resend(body == null ? null : body.email());
        return HttpResponse.accepted().body(Map.of("message",
                "If that address belongs to an unconfirmed account, a new link is on its way."));
    }

    @Post(value = "/login", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<AuthResponse> login(@Body LoginRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        User user = authService.login(body.username(), body.password());
        return withTokens(tokenService.issue(user));
    }

    @Post("/refresh")
    public HttpResponse<?> refresh(HttpRequest<?> request) {
        return refreshCookie.extract(request)
                .flatMap(tokenService::refresh)
                .<HttpResponse<?>>map(this::withTokens)
                .orElseGet(() -> HttpResponse.status(HttpStatus.UNAUTHORIZED)
                        .cookie(refreshCookie.buildExpired())
                        .body(ErrorResponse.of("Your session has expired. Please sign in again.")));
    }

    @Post("/logout")
    public HttpResponse<?> logout(HttpRequest<?> request) {
        refreshCookie.extract(request).ifPresent(tokenService::revoke);
        return HttpResponse.noContent().cookie(refreshCookie.buildExpired());
    }

    @Get("/me")
    public SessionResponse me(HttpRequest<?> request) {
        UserResponse user = JwtAuthenticationFilter.bearerToken(request)
                .flatMap(jwtService::parseAccessToken)
                .flatMap(userDao::findById)
                .map(found -> new UserResponse(found.id(), found.username(), found.avatarUrl()))
                .orElse(null);
        return new SessionResponse(user);
    }

    private HttpResponse<AuthResponse> withTokens(TokenService.Tokens tokens) {
        User user = tokens.user();
        AuthResponse body = new AuthResponse(tokens.accessToken(), tokenService.accessTokenExpiresInSeconds(),
                new UserResponse(user.id(), user.username(), user.avatarUrl()));
        return HttpResponse.ok(body).cookie(refreshCookie.build(tokens.refreshToken()));
    }
}

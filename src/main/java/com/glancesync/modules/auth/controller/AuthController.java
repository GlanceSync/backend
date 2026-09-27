package com.glancesync.modules.auth.controller;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.dto.LoginRequest;
import com.glancesync.modules.auth.dto.RegisterRequest;
import com.glancesync.modules.auth.dto.SessionResponse;
import com.glancesync.modules.auth.service.AuthService;
import com.glancesync.modules.auth.service.SessionService;
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
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Auth")
@Controller("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final UserDao userDao;

    public AuthController(AuthService authService, SessionService sessionService, UserDao userDao) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.userDao = userDao;
    }

    @Post(value = "/register", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<UserResponse> register(@Body RegisterRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        User user = authService.register(body.username(), body.password(), body.confirmPassword());
        return HttpResponse.created(new UserResponse(user.id(), user.username(), user.avatarUrl()));
    }

    @Post(value = "/login", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<UserResponse> login(@Body LoginRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        User user = authService.login(body.username(), body.password());
        String token = sessionService.startSession(user.id());
        return HttpResponse.ok(new UserResponse(user.id(), user.username(), user.avatarUrl()))
                .cookie(sessionService.buildCookie(token));
    }

    @Post("/logout")
    public HttpResponse<?> logout(HttpRequest<?> request) {
        request.getCookies()
                .findCookie(SessionService.COOKIE_NAME)
                .ifPresent(cookie -> sessionService.endSession(cookie.getValue()));
        return HttpResponse.noContent().cookie(sessionService.buildExpiredCookie());
    }

    /**
     * Reports the current session. Always 200: "signed out" is a normal answer,
     * so the browser console stays clean for anonymous visitors.
     */
    @Get("/me")
    public SessionResponse me(HttpRequest<?> request) {
        UserResponse user = request.getCookies()
                .findCookie(SessionService.COOKIE_NAME)
                .flatMap(cookie -> sessionService.resolveUserId(cookie.getValue()))
                .flatMap(userDao::findById)
                .map(found -> new UserResponse(found.id(), found.username(), found.avatarUrl()))
                .orElse(null);
        return new SessionResponse(user);
    }
}

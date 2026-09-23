package com.glancesync.auth;

import com.glancesync.api.ApiException;
import com.glancesync.api.Dtos;
import com.glancesync.user.User;
import com.glancesync.user.UserDao;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

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
    public HttpResponse<Dtos.UserResponse> register(@Body Dtos.RegisterRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        User user = authService.register(body.username(), body.password(), body.confirmPassword());
        return HttpResponse.created(new Dtos.UserResponse(user.id(), user.username(), user.avatarUrl()));
    }

    @Post(value = "/login", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<Dtos.UserResponse> login(@Body Dtos.LoginRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        User user = authService.login(body.username(), body.password());
        String token = sessionService.startSession(user.id());
        return HttpResponse.ok(new Dtos.UserResponse(user.id(), user.username(), user.avatarUrl()))
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
    public Dtos.SessionResponse me(HttpRequest<?> request) {
        Dtos.UserResponse user = request.getCookies()
                .findCookie(SessionService.COOKIE_NAME)
                .flatMap(cookie -> sessionService.resolveUserId(cookie.getValue()))
                .flatMap(userDao::findById)
                .map(found -> new Dtos.UserResponse(found.id(), found.username(), found.avatarUrl()))
                .orElse(null);
        return new Dtos.SessionResponse(user);
    }
}

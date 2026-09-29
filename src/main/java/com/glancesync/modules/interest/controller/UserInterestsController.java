package com.glancesync.modules.interest.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.interest.dto.InterestRequest;
import com.glancesync.modules.interest.dto.UserInterestsRequest;
import com.glancesync.modules.interest.entity.Interest;
import com.glancesync.modules.interest.service.InterestService;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "User Interests")
@Controller("/api/users")
public class UserInterestsController {

    private final InterestService interestService;

    public UserInterestsController(InterestService interestService) {
        this.interestService = interestService;
    }

    @Get("/me/interests")
    public List<Interest> mine(HttpRequest<?> request) {
        return interestService.forUser(selfId(request));
    }

    @Get("/{userId}/interests")
    public List<Interest> ofUser(@PathVariable UUID userId) {
        return interestService.forUser(userId);
    }

    @Put(value = "/me/interests", consumes = MediaType.APPLICATION_JSON)
    public List<Interest> replace(HttpRequest<?> request, @Body @Nullable UserInterestsRequest body) {
        return interestService.replaceForUser(selfId(request), body == null ? null : body.names());
    }

    @Post(value = "/me/interests", consumes = MediaType.APPLICATION_JSON)
    public List<Interest> add(HttpRequest<?> request, @Body @Nullable InterestRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        return interestService.addToUser(selfId(request), body.name());
    }

    @Delete("/me/interests/{interestId}")
    public List<Interest> remove(HttpRequest<?> request, @PathVariable UUID interestId) {
        return interestService.removeFromUser(selfId(request), interestId);
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

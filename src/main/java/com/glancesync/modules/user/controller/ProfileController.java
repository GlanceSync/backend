package com.glancesync.modules.user.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.user.dto.LocationRequest;
import com.glancesync.modules.user.dto.MapVisibilityRequest;
import com.glancesync.modules.user.dto.ProfileRequest;
import com.glancesync.modules.user.dto.ProfileResponse;
import com.glancesync.modules.user.service.ProfileService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users")
@Controller("/api/users")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Get("/me/profile")
    public ProfileResponse mine(HttpRequest<?> request) {
        return profileService.mine(selfId(request));
    }

    @Get("/{userId}/profile")
    public ProfileResponse ofUser(HttpRequest<?> request, @PathVariable UUID userId) {
        return profileService.view(selfId(request), userId);
    }

    @Put(value = "/me/profile", consumes = MediaType.APPLICATION_JSON)
    public ProfileResponse update(HttpRequest<?> request, @Body @Nullable ProfileRequest body) {
        return profileService.update(selfId(request), body);
    }

    @Put(value = "/me/location", consumes = MediaType.APPLICATION_JSON)
    public ProfileResponse updateLocation(HttpRequest<?> request, @Body @Nullable LocationRequest body) {
        return profileService.updateLocation(selfId(request), body);
    }

    @Put(value = "/me/map-visibility", consumes = MediaType.APPLICATION_JSON)
    public ProfileResponse updateMapVisibility(HttpRequest<?> request, @Body @Nullable MapVisibilityRequest body) {
        return profileService.updateMapVisibility(selfId(request), body);
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

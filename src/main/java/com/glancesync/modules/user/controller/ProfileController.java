package com.glancesync.modules.user.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.discovery.entity.DiscoveryPreferences;
import com.glancesync.modules.discovery.service.DiscoveryService;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.user.dto.LocationRequest;
import com.glancesync.modules.user.dto.MapVisibilityRequest;
import com.glancesync.modules.user.dto.ProfileRequest;
import com.glancesync.modules.user.dto.ProfileResponse;
import com.glancesync.modules.user.service.ProfileService;
import com.glancesync.web.Nullable;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpStatus;
import com.glancesync.web.MediaType;
import com.glancesync.web.annotation.Body;
import com.glancesync.web.annotation.Controller;
import com.glancesync.web.annotation.Get;
import com.glancesync.web.annotation.PathVariable;
import com.glancesync.web.annotation.Put;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users")
@Controller("/api/users")
public class ProfileController {

    private final ProfileService profileService;
    private final DiscoveryService discoveryService;

    public ProfileController(ProfileService profileService, DiscoveryService discoveryService) {
        this.profileService = profileService;
        this.discoveryService = discoveryService;
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

    /** The filters GET /api/users/discover applies. Fields never saved come back null. */
    @Get("/me/preferences")
    public DiscoveryPreferences preferences(HttpRequest<?> request) {
        return discoveryService.preferences(selfId(request));
    }

    /** Replaces all saved filters at once; a field left out is cleared. */
    @Put(value = "/me/preferences", consumes = MediaType.APPLICATION_JSON)
    public DiscoveryPreferences savePreferences(HttpRequest<?> request,
                                                @Body @Nullable DiscoveryPreferences body) {
        return discoveryService.savePreferences(selfId(request), body);
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

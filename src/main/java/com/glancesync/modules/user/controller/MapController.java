package com.glancesync.modules.user.controller;

import java.util.List;
import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.user.dto.MapUserResponse;
import com.glancesync.modules.user.service.MapService;
import com.glancesync.web.Nullable;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpStatus;
import com.glancesync.web.annotation.Controller;
import com.glancesync.web.annotation.Get;
import com.glancesync.web.annotation.QueryValue;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users")
@Controller("/api/users")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    /** Everyone who shares their location inside the visible part of the map, nearest first. */
    @Get("/map")
    public List<MapUserResponse> map(HttpRequest<?> request,
                                     @QueryValue @Nullable Double minLat, @QueryValue @Nullable Double minLng,
                                     @QueryValue @Nullable Double maxLat, @QueryValue @Nullable Double maxLng) {
        return mapService.inBounds(selfId(request), minLat, minLng, maxLat, maxLng);
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

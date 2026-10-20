package com.glancesync.modules.discovery.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.discovery.dto.DiscoveryCard;
import com.glancesync.modules.discovery.entity.DiscoveryPreferences;
import com.glancesync.modules.discovery.service.DiscoveryService;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.web.Nullable;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpStatus;
import com.glancesync.web.annotation.Controller;
import com.glancesync.web.annotation.Get;
import com.glancesync.web.annotation.QueryValue;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Arrays;
import java.util.List;

@Tag(name = "Discovery")
@Controller("/api/users")
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Get("/discover")
    public List<DiscoveryCard> discover(HttpRequest<?> request,
                                        @QueryValue @Nullable Integer minAge, @QueryValue @Nullable Integer maxAge,
                                        @QueryValue @Nullable Integer minFame, @QueryValue @Nullable Integer maxFame,
                                        @QueryValue @Nullable Double distance, @QueryValue @Nullable String city,
                                        @QueryValue @Nullable String tags, @QueryValue @Nullable Integer minCommonTags,
                                        @QueryValue @Nullable String sort,
                                        @QueryValue @Nullable String order, @QueryValue @Nullable Integer limit,
                                        @QueryValue @Nullable Integer offset) {
        DiscoveryPreferences overrides = new DiscoveryPreferences(minAge, maxAge, minFame, maxFame, distance, city,
                tagList(tags), minCommonTags, sort, order);
        return discoveryService.discover(selfId(request), overrides, limit, offset);
    }

    /** Advanced search: the same criteria as discover, but the saved preferences are ignored. */
    @Get("/search")
    public List<DiscoveryCard> search(HttpRequest<?> request,
                                      @QueryValue @Nullable Integer minAge, @QueryValue @Nullable Integer maxAge,
                                      @QueryValue @Nullable Integer minFame, @QueryValue @Nullable Integer maxFame,
                                      @QueryValue @Nullable Double distance, @QueryValue @Nullable String city,
                                      @QueryValue @Nullable String tags, @QueryValue @Nullable Integer minCommonTags,
                                      @QueryValue @Nullable String sort,
                                      @QueryValue @Nullable String order, @QueryValue @Nullable Integer limit,
                                      @QueryValue @Nullable Integer offset) {
        DiscoveryPreferences criteria = new DiscoveryPreferences(minAge, maxAge, minFame, maxFame, distance, city,
                tagList(tags), minCommonTags, sort, order);
        return discoveryService.search(selfId(request), criteria, limit, offset);
    }

    private static List<String> tagList(String tags) {
        return tags == null ? List.of() : Arrays.asList(tags.split(","));
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

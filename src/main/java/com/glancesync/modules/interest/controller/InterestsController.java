package com.glancesync.modules.interest.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.interest.dto.InterestRequest;
import com.glancesync.modules.interest.entity.Interest;
import com.glancesync.modules.interest.service.InterestService;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Interests")
@Controller("/api/interests")
public class InterestsController {

    private final InterestService interestService;

    public InterestsController(InterestService interestService) {
        this.interestService = interestService;
    }

    @Get
    public List<Interest> search(@QueryValue @Nullable String q) {
        return interestService.search(q);
    }

    @Get("/{interestId}")
    public Interest get(@PathVariable UUID interestId) {
        return interestService.get(interestId);
    }

    @Post(consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<Interest> create(HttpRequest<?> request, @Body @Nullable InterestRequest body) {
        InterestService.Created created = interestService.create(selfId(request), requireName(body));
        return created.isNew()
                ? HttpResponse.created(created.interest())
                : HttpResponse.ok(created.interest());
    }

    @Put(value = "/{interestId}", consumes = MediaType.APPLICATION_JSON)
    public Interest rename(HttpRequest<?> request, @PathVariable UUID interestId,
                           @Body @Nullable InterestRequest body) {
        return interestService.rename(selfId(request), interestId, requireName(body));
    }

    @Delete("/{interestId}")
    public HttpResponse<?> delete(HttpRequest<?> request, @PathVariable UUID interestId) {
        interestService.delete(selfId(request), interestId);
        return HttpResponse.noContent();
    }

    private String requireName(InterestRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        return body.name();
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

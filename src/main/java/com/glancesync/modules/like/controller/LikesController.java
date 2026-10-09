package com.glancesync.modules.like.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.like.dto.LikeStatus;
import com.glancesync.modules.like.service.LikeService;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.user.dto.ActivityResponse;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpStatus;
import com.glancesync.web.annotation.Controller;
import com.glancesync.web.annotation.Delete;
import com.glancesync.web.annotation.Get;
import com.glancesync.web.annotation.PathVariable;
import com.glancesync.web.annotation.Post;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Likes")
@Controller("/api/users")
public class LikesController {

    private final LikeService likeService;

    public LikesController(LikeService likeService) {
        this.likeService = likeService;
    }

    @Get("/me/liked-by")
    public List<ActivityResponse> likedBy(HttpRequest<?> request) {
        return likeService.likedBy(selfId(request));
    }

    @Post("/{userId}/like")
    public LikeStatus like(HttpRequest<?> request, @PathVariable UUID userId) {
        return likeService.like(selfId(request), userId);
    }

    @Delete("/{userId}/like")
    public LikeStatus unlike(HttpRequest<?> request, @PathVariable UUID userId) {
        return likeService.unlike(selfId(request), userId);
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

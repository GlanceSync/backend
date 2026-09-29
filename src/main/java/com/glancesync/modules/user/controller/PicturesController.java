package com.glancesync.modules.user.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.user.dto.PictureResponse;
import com.glancesync.modules.user.dto.UserResponse;
import com.glancesync.modules.user.entity.Picture;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.service.PictureService;
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
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * Profile pictures. Uploads are the raw image bytes; the list endpoints return metadata
 * and each picture's URL. Writes return the caller's full, updated list.
 */
@Tag(name = "Pictures")
@Controller("/api/users")
public class PicturesController {

    private final PictureService pictureService;

    public PicturesController(PictureService pictureService) {
        this.pictureService = pictureService;
    }

    @Get("/me/pictures")
    public List<PictureResponse> mine(HttpRequest<?> request) {
        return toResponses(pictureService.forUser(selfId(request)));
    }

    @Get("/{userId}/pictures")
    public List<PictureResponse> ofUser(@PathVariable UUID userId) {
        return toResponses(pictureService.forUser(userId));
    }

    /** Public, so it works as an <img> src. The bytes behind an id never change. */
    @Get("/{userId}/pictures/{pictureId}")
    public HttpResponse<byte[]> show(@PathVariable UUID userId, @PathVariable UUID pictureId) {
        return pictureService.data(userId, pictureId)
                .map(picture -> HttpResponse.ok(picture.data())
                        .contentType(picture.contentType())
                        .header("Cache-Control", "private, max-age=31536000, immutable")
                        .header("X-Content-Type-Options", "nosniff"))
                .orElseGet(HttpResponse::notFound);
    }

    @Post(value = "/me/pictures", consumes = {MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, "image/webp"})
    public HttpResponse<List<PictureResponse>> add(HttpRequest<?> request, @Body byte[] body) {
        return HttpResponse.created(toResponses(pictureService.add(selfId(request), body)));
    }

    @Put("/me/pictures/{pictureId}/profile")
    public List<PictureResponse> setProfile(HttpRequest<?> request, @PathVariable UUID pictureId) {
        return toResponses(pictureService.setProfile(selfId(request), pictureId));
    }

    @Delete("/me/pictures/{pictureId}")
    public List<PictureResponse> delete(HttpRequest<?> request, @PathVariable UUID pictureId) {
        return toResponses(pictureService.delete(selfId(request), pictureId));
    }

    @Put(value = "/me/avatar", consumes = {MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, "image/webp"})
    public UserResponse replaceAvatar(HttpRequest<?> request, @Body byte[] body) {
        return toUserResponse(pictureService.replaceProfile(selfId(request), body));
    }

    @Delete("/me/avatar")
    public UserResponse removeAvatar(HttpRequest<?> request) {
        return toUserResponse(pictureService.removeProfile(selfId(request)));
    }

    private static List<PictureResponse> toResponses(List<Picture> pictures) {
        return pictures.stream().map(PictureResponse::of).toList();
    }

    private static UserResponse toUserResponse(User user) {
        return UserResponse.of(user);
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

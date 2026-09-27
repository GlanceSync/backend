package com.glancesync.modules.user.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.security.filter.AuthenticationFilter;
import com.glancesync.modules.user.dto.UserResponse;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.AvatarDao;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Profile photos. The browser crops and shrinks the image before sending it, so the
 * body is the finished file; the server's job is to refuse anything that is not one.
 */
@Tag(name = "Users")
@Controller("/api/users")
public class AvatarController {

    /** Far above what a 256px square needs, far below what could hurt the database. */
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final AvatarDao avatarDao;
    private final UserDao userDao;

    public AvatarController(AvatarDao avatarDao, UserDao userDao) {
        this.avatarDao = avatarDao;
        this.userDao = userDao;
    }

    @Put(value = "/me/avatar", consumes = {MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, "image/webp"})
    public UserResponse upload(HttpRequest<?> request, @Body byte[] body) {
        UUID selfId = selfId(request);
        if (body == null || body.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an image to upload.");
        }
        if (body.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.REQUEST_ENTITY_TOO_LARGE, "That image is too large. The limit is 2 MB.");
        }

        // The declared type is only a claim; the stored type comes from the bytes.
        String contentType = sniffImageType(body);
        if (contentType == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use a JPEG, PNG or WebP image.");
        }

        avatarDao.save(selfId, contentType, body);
        return toResponse(selfId);
    }

    @Delete("/me/avatar")
    public UserResponse remove(HttpRequest<?> request) {
        UUID selfId = selfId(request);
        avatarDao.delete(selfId);
        return toResponse(selfId);
    }

    @Get("/{userId}/avatar")
    public HttpResponse<byte[]> show(@PathVariable UUID userId) {
        return avatarDao.find(userId)
                .map(avatar -> HttpResponse.ok(avatar.data())
                        .contentType(avatar.contentType())
                        // The URL carries a version, so a changed photo is a new URL.
                        .header("Cache-Control", "private, max-age=31536000, immutable")
                        .header("X-Content-Type-Options", "nosniff"))
                .orElseGet(HttpResponse::notFound);
    }

    private UserResponse toResponse(UUID userId) {
        User user = userDao.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That account no longer exists."));
        return new UserResponse(user.id(), user.username(), user.avatarUrl());
    }

    /** Recognises the formats we accept by their signatures, or returns null. */
    static String sniffImageType(byte[] data) {
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
            return MediaType.IMAGE_JPEG;
        }
        if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return MediaType.IMAGE_PNG;
        }
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(AuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

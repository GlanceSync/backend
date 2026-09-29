package com.glancesync.modules.user.dto;

import java.util.UUID;
import com.glancesync.modules.user.entity.Picture;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record PictureResponse(UUID id, String url, boolean profile, Instant createdAt) {

    public static PictureResponse of(Picture picture) {
        return new PictureResponse(picture.id(), picture.url(), picture.profile(), picture.createdAt());
    }
}

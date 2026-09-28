package com.glancesync.modules.like.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record LikeStatus(boolean likedByMe, boolean likesMe, boolean connected) {
}

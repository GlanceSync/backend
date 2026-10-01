package com.glancesync.modules.user.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.block.repo.BlockDao;
import com.glancesync.modules.block.service.BlockService;
import com.glancesync.modules.chat.entity.Message;
import com.glancesync.modules.chat.repo.MessageDao;
import com.glancesync.modules.like.repo.LikeDao;
import com.glancesync.modules.realtime.service.PresenceRegistry;
import com.glancesync.modules.user.dto.PersonResponse;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "Users")
@Controller("/api/users")
public class UsersController {

    private final UserDao userDao;
    private final PresenceRegistry presence;
    private final MessageDao messageDao;
    private final BlockDao blockDao;
    private final BlockService blockService;
    private final LikeDao likeDao;

    public UsersController(UserDao userDao, PresenceRegistry presence, MessageDao messageDao,
                           BlockDao blockDao, BlockService blockService, LikeDao likeDao) {
        this.userDao = userDao;
        this.presence = presence;
        this.messageDao = messageDao;
        this.blockDao = blockDao;
        this.blockService = blockService;
        this.likeDao = likeDao;
    }

    /**
     * The people list: only the people this user is connected with (a mutual like),
     * since those are the only conversations worth offering. Presence is read from the live socket registry, so the initial
     * page load and the subsequent WebSocket updates agree with each other.
     */
    @Get
    public List<PersonResponse> list(HttpRequest<?> request) {
        UUID selfId = selfId(request);

        // One query each for previews and block state, rather than one per person.
        Map<UUID, Message> latest = messageDao.latestPerCounterpart(selfId);
        Set<UUID> iBlocked = blockDao.blockedBy(selfId);
        Set<UUID> blockedMe = blockDao.blockersOf(selfId);
        Set<UUID> connections = likeDao.connectionsOf(selfId);

        return userDao.findAllExcept(selfId).stream()
                .filter(user -> connections.contains(user.id()))
                .map(user -> {
                    Message last = latest.get(user.id());
                    return new PersonResponse(
                            user.id(),
                            user.username(),
                            user.avatarUrl(),
                            presence.isOnline(user.id()),
                            user.lastSeenAt(),
                            last == null ? null : last.body(),
                            last == null ? null : last.createdAt(),
                            last == null ? null : last.senderId().equals(selfId),
                            iBlocked.contains(user.id()),
                            blockedMe.contains(user.id()));
                })
                .toList();
    }

    @Post("/{otherUserId}/block")
    public HttpResponse<?> block(HttpRequest<?> request, @PathVariable UUID otherUserId) {
        blockService.block(selfId(request), otherUserId);
        return HttpResponse.noContent();
    }

    @Post("/{otherUserId}/unblock")
    public HttpResponse<?> unblock(HttpRequest<?> request, @PathVariable UUID otherUserId) {
        blockService.unblock(selfId(request), otherUserId);
        return HttpResponse.noContent();
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

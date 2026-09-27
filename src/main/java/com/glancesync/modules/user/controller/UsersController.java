package com.glancesync.modules.user.controller;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.filter.AuthenticationFilter;
import com.glancesync.modules.block.repo.BlockDao;
import com.glancesync.modules.block.service.BlockService;
import com.glancesync.modules.chat.entity.Message;
import com.glancesync.modules.chat.repo.MessageDao;
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

    public UsersController(UserDao userDao, PresenceRegistry presence, MessageDao messageDao,
                           BlockDao blockDao, BlockService blockService) {
        this.userDao = userDao;
        this.presence = presence;
        this.messageDao = messageDao;
        this.blockDao = blockDao;
        this.blockService = blockService;
    }

    /**
     * The people list. Presence is read from the live socket registry, so the initial
     * page load and the subsequent WebSocket updates agree with each other.
     */
    @Get
    public List<PersonResponse> list(HttpRequest<?> request) {
        long selfId = selfId(request);

        // One query each for previews and block state, rather than one per person.
        Map<Long, Message> latest = messageDao.latestPerCounterpart(selfId);
        Set<Long> iBlocked = blockDao.blockedBy(selfId);
        Set<Long> blockedMe = blockDao.blockersOf(selfId);

        return userDao.findAllExcept(selfId).stream()
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
                            last == null ? null : last.senderId() == selfId,
                            iBlocked.contains(user.id()),
                            blockedMe.contains(user.id()));
                })
                .toList();
    }

    @Post("/{otherUserId}/block")
    public HttpResponse<?> block(HttpRequest<?> request, @PathVariable long otherUserId) {
        blockService.block(selfId(request), otherUserId);
        return HttpResponse.noContent();
    }

    @Post("/{otherUserId}/unblock")
    public HttpResponse<?> unblock(HttpRequest<?> request, @PathVariable long otherUserId) {
        blockService.unblock(selfId(request), otherUserId);
        return HttpResponse.noContent();
    }

    private long selfId(HttpRequest<?> request) {
        return request.getAttribute(AuthenticationFilter.USER_ID_ATTRIBUTE, Long.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

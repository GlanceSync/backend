package com.glancesync.user;

import com.glancesync.api.ApiException;
import com.glancesync.block.BlockService;
import com.glancesync.block.BlockDao;
import com.glancesync.api.Dtos;
import com.glancesync.auth.AuthenticationFilter;
import com.glancesync.chat.Message;
import com.glancesync.chat.MessageDao;
import com.glancesync.realtime.PresenceRegistry;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public List<Dtos.PersonResponse> list(HttpRequest<?> request) {
        long selfId = selfId(request);

        // One query each for previews and block state, rather than one per person.
        Map<Long, Message> latest = messageDao.latestPerCounterpart(selfId);
        Set<Long> iBlocked = blockDao.blockedBy(selfId);
        Set<Long> blockedMe = blockDao.blockersOf(selfId);

        return userDao.findAllExcept(selfId).stream()
                .map(user -> {
                    Message last = latest.get(user.id());
                    return new Dtos.PersonResponse(
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

package com.glancesync.modules.chat.controller;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.filter.AuthenticationFilter;
import com.glancesync.modules.chat.dto.ChatEntry;
import com.glancesync.modules.chat.service.ChatService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * History only. Sending happens over the WebSocket, so a message takes one path and
 * arrives the same way whether or not the recipient is connected.
 */
@Tag(name = "Messages")
@Controller("/api/messages")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Get("/{otherUserId}")
    public List<ChatEntry> conversation(HttpRequest<?> request, @PathVariable long otherUserId) {
        long selfId = selfId(request);
        if (otherUserId == selfId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot message yourself.");
        }
        return chatService.conversation(selfId, otherUserId);
    }

    private long selfId(HttpRequest<?> request) {
        return request.getAttribute(AuthenticationFilter.USER_ID_ATTRIBUTE, Long.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

package com.glancesync.modules.call.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.security.filter.AuthenticationFilter;
import com.glancesync.modules.call.dto.IceServer;
import com.glancesync.modules.call.dto.IceServersResponse;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * The ICE servers a browser needs to place a call. STUN finds a direct path; when the
 * two networks cannot reach each other, TURN relays the media through our server.
 *
 * TURN credentials follow coturn's shared-secret scheme: the username is an expiry time,
 * the password an HMAC of it. They are minted per request and expire on their own, so
 * no long-lived TURN password ever reaches the browser.
 */
@Tag(name = "Calls")
@Controller("/api/calls")
public class CallsController {

    private static final String PUBLIC_STUN = "stun:stun.l.google.com:19302";

    /** Long enough for any call, short enough that a leaked credential soon dies. */
    private static final Duration CREDENTIAL_TTL = Duration.ofHours(12);

    private final String turnSecret;
    private final List<String> turnUrls;

    public CallsController(@Value("${glancesync.turn.secret:}") String turnSecret,
                           @Value("${glancesync.turn.urls:}") String turnUrls) {
        this.turnSecret = turnSecret;
        this.turnUrls = Arrays.stream(turnUrls.split(","))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toList();
    }

    @Get("/ice-servers")
    public IceServersResponse iceServers(HttpRequest<?> request) {
        UUID selfId = request.getAttribute(AuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));

        List<IceServer> servers = new ArrayList<>();
        servers.add(new IceServer(List.of(PUBLIC_STUN), null, null));

        // Without TURN configured (local development), calls fall back to STUN alone.
        if (!turnSecret.isEmpty() && !turnUrls.isEmpty()) {
            long expiresAt = Instant.now().plus(CREDENTIAL_TTL).getEpochSecond();
            String username = expiresAt + ":" + selfId;
            servers.add(new IceServer(turnUrls, username, sign(username)));
        }
        return new IceServersResponse(servers);
    }

    private String sign(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(turnSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is unavailable", e);
        }
    }
}

package com.glancesync.modules.oauth.service;

import com.glancesync.modules.oauth.dto.OAuthUserResponse;
import com.glancesync.modules.oauth.enums.AuthProvider;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.session.Session;

import java.net.URI;

/** One OAuth2 provider: where to send the browser, and who came back. */
public interface ProviderAuthService {

    AuthProvider provider();

    boolean isEnabled();

    URI getAuthorizationUrl(Session session);

    OAuthUserResponse authenticate(String code, @Nullable String state, @Nullable Session session);
}

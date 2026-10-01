package com.glancesync.modules.oauth.serviceImpl;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.oauth.enums.AuthProvider;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretPost;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

final class OAuthRequests {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthRequests.class);
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private OAuthRequests() {
    }

    static String newState(Session session, String sessionKey) {
        String state = UUID.randomUUID().toString();
        session.put(sessionKey, state);
        return state;
    }

    static void validateState(@Nullable Session session, String sessionKey, @Nullable String state) {
        if (session == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This sign-in has expired. Please try again.");
        }

        String expectedState = session.get(sessionKey, String.class).orElse(null);
        session.remove(sessionKey);

        if (expectedState == null || !expectedState.equals(state)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This sign-in has expired. Please try again.");
        }
    }

    static AccessToken exchangeCode(AuthProvider provider, URI tokenUrl, String clientId, String clientSecret,
                                    String code, URI redirectUri) {
        TokenRequest request = new TokenRequest.Builder(
                tokenUrl,
                new ClientSecretPost(new ClientID(clientId), new Secret(clientSecret)),
                new AuthorizationCodeGrant(new AuthorizationCode(code), redirectUri))
                .build();
        try {
            TokenResponse response = TokenResponse.parse(withTimeouts(request.toHTTPRequest()).send());
            if (!response.indicatesSuccess()) {
                LOG.warn("{} refused the authorization code: {}", provider.displayName(),
                        response.toErrorResponse().getErrorObject());
                throw failed(provider, HttpStatus.UNAUTHORIZED);
            }
            return response.toSuccessResponse().getTokens().getAccessToken();
        } catch (IOException | ParseException e) {
            LOG.warn("Token request to {} failed: {}", provider.displayName(), e.getMessage());
            throw failed(provider, HttpStatus.BAD_GATEWAY);
        }
    }

    static Map<String, Object> getJson(AuthProvider provider, URI url, AccessToken accessToken) {
        HTTPRequest request = withTimeouts(new HTTPRequest(HTTPRequest.Method.GET, url));
        request.setAuthorization(accessToken.toAuthorizationHeader());
        try {
            HTTPResponse response = request.send();
            if (!response.indicatesSuccess()) {
                LOG.warn("{} refused the profile request: HTTP {}", provider.displayName(),
                        response.getStatusCode());
                throw failed(provider, HttpStatus.BAD_GATEWAY);
            }
            return JSONObjectUtils.parse(response.getBody());
        } catch (IOException | ParseException e) {
            LOG.warn("Profile request to {} failed: {}", provider.displayName(), e.getMessage());
            throw failed(provider, HttpStatus.BAD_GATEWAY);
        }
    }

    static String string(@Nullable Object value) {
        return value == null ? null : value.toString();
    }

    private static ApiException failed(AuthProvider provider, HttpStatus status) {
        return new ApiException(status, "Signing in with " + provider.displayName() + " failed. Please try again.");
    }

    private static HTTPRequest withTimeouts(HTTPRequest request) {
        request.setConnectTimeout(CONNECT_TIMEOUT_MS);
        request.setReadTimeout(READ_TIMEOUT_MS);
        return request;
    }
}

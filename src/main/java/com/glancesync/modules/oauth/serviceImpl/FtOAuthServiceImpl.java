package com.glancesync.modules.oauth.serviceImpl;

import com.glancesync.modules.oauth.dto.OAuthUserResponse;
import com.glancesync.modules.oauth.enums.AuthProvider;
import com.glancesync.modules.oauth.service.ProviderAuthService;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.glancesync.web.Value;
import com.glancesync.web.Nullable;
import com.glancesync.web.Session;

import java.net.URI;
import java.util.Map;

import static com.glancesync.modules.oauth.serviceImpl.OAuthRequests.string;

public class FtOAuthServiceImpl implements ProviderAuthService {

    private static final String STATE_SESSION_KEY = "oauth2_state";

    private final String clientId;
    private final String clientSecret;
    private final URI authorizeUrl;
    private final URI tokenUrl;
    private final URI meUrl;
    private final URI redirectUri;

    public FtOAuthServiceImpl(@Value("${glancesync.oauth.providers.42.client-id:}") String clientId,
                              @Value("${glancesync.oauth.providers.42.client-secret:}") String clientSecret,
                              @Value("${glancesync.oauth.providers.42.authorize-url}") URI authorizeUrl,
                              @Value("${glancesync.oauth.providers.42.token-url}") URI tokenUrl,
                              @Value("${glancesync.oauth.providers.42.userinfo-url}") URI meUrl,
                              @Value("${glancesync.oauth.providers.42.redirect-uri}") URI redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.meUrl = meUrl;
        this.redirectUri = redirectUri;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.INTRA;
    }

    @Override
    public boolean isEnabled() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    @Override
    public URI getAuthorizationUrl(Session session) {
        String state = OAuthRequests.newState(session, STATE_SESSION_KEY);

        return new AuthorizationRequest.Builder(ResponseType.CODE, new ClientID(clientId))
                .endpointURI(authorizeUrl)
                .redirectionURI(redirectUri)
                .scope(new Scope("public"))
                .state(new State(state))
                .build()
                .toURI();
    }

    @Override
    public OAuthUserResponse authenticate(String code, @Nullable String state, @Nullable Session session) {
        OAuthRequests.validateState(session, STATE_SESSION_KEY, state);

        AccessToken accessToken = OAuthRequests.exchangeCode(provider(), tokenUrl, clientId, clientSecret,
                code, redirectUri);
        Map<String, Object> me = OAuthRequests.getJson(provider(), meUrl, accessToken);

        Object image = me.get("image");
        String imageUrl = image instanceof Map<?, ?> imageMap ? string(imageMap.get("link")) : null;
        // 42 accounts are school accounts, so the address is always verified.
        return new OAuthUserResponse(string(me.get("id")), string(me.get("login")), string(me.get("email")),
                true, string(me.get("first_name")), string(me.get("last_name")), imageUrl);
    }
}

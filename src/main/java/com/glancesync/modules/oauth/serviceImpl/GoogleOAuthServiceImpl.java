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

public class GoogleOAuthServiceImpl implements ProviderAuthService {

    private static final String STATE_SESSION_KEY = "oauth2_google_state";

    private final String clientId;
    private final String clientSecret;
    private final URI authorizeUrl;
    private final URI tokenUrl;
    private final URI userinfoUrl;
    private final URI redirectUri;

    public GoogleOAuthServiceImpl(@Value("${glancesync.oauth.providers.google.client-id:}") String clientId,
                                  @Value("${glancesync.oauth.providers.google.client-secret:}") String clientSecret,
                                  @Value("${glancesync.oauth.providers.google.authorize-url}") URI authorizeUrl,
                                  @Value("${glancesync.oauth.providers.google.token-url}") URI tokenUrl,
                                  @Value("${glancesync.oauth.providers.google.userinfo-url}") URI userinfoUrl,
                                  @Value("${glancesync.oauth.providers.google.redirect-uri}") URI redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.userinfoUrl = userinfoUrl;
        this.redirectUri = redirectUri;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
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
                .scope(new Scope("openid", "email", "profile"))
                .state(new State(state))
                .customParameter("prompt", "select_account")
                .build()
                .toURI();
    }

    @Override
    public OAuthUserResponse authenticate(String code, @Nullable String state, @Nullable Session session) {
        OAuthRequests.validateState(session, STATE_SESSION_KEY, state);

        AccessToken accessToken = OAuthRequests.exchangeCode(provider(), tokenUrl, clientId, clientSecret,
                code, redirectUri);
        Map<String, Object> me = OAuthRequests.getJson(provider(), userinfoUrl, accessToken);

        return new OAuthUserResponse(string(me.get("sub")), null, string(me.get("email")),
                "true".equals(string(me.get("email_verified"))), string(me.get("given_name")),
                string(me.get("family_name")), string(me.get("picture")));
    }
}

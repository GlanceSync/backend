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

public class FacebookOAuthServiceImpl implements ProviderAuthService {

    private static final String STATE_SESSION_KEY = "oauth2_facebook_state";

    private static final String PROFILE_FIELDS = "id,email,first_name,last_name";

    private final String clientId;
    private final String clientSecret;
    private final URI authorizeUrl;
    private final URI tokenUrl;
    private final URI userinfoUrl;
    private final URI redirectUri;

    public FacebookOAuthServiceImpl(@Value("${glancesync.oauth.providers.facebook.client-id:}") String clientId,
                                    @Value("${glancesync.oauth.providers.facebook.client-secret:}") String clientSecret,
                                    @Value("${glancesync.oauth.providers.facebook.authorize-url}") URI authorizeUrl,
                                    @Value("${glancesync.oauth.providers.facebook.token-url}") URI tokenUrl,
                                    @Value("${glancesync.oauth.providers.facebook.userinfo-url}") URI userinfoUrl,
                                    @Value("${glancesync.oauth.providers.facebook.redirect-uri}") URI redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.userinfoUrl = userinfoUrl;
        this.redirectUri = redirectUri;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.FACEBOOK;
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
                .scope(new Scope("email", "public_profile"))
                .state(new State(state))
                .build()
                .toURI();
    }

    @Override
    public OAuthUserResponse authenticate(String code, @Nullable String state, @Nullable Session session) {
        OAuthRequests.validateState(session, STATE_SESSION_KEY, state);

        AccessToken accessToken = OAuthRequests.exchangeCode(provider(), tokenUrl, clientId, clientSecret,
                code, redirectUri);
        URI profileUrl = URI.create(userinfoUrl + (userinfoUrl.getQuery() == null ? "?" : "&")
                + "fields=" + PROFILE_FIELDS);
        Map<String, Object> me = OAuthRequests.getJson(provider(), profileUrl, accessToken);

        String id = string(me.get("id"));
        String email = string(me.get("email"));
        // Facebook only returns an address once it is confirmed. The picture URL in the profile
        // is a signed CDN link that expires; this one redirects to the current picture.
        return new OAuthUserResponse(id, null, email, email != null, string(me.get("first_name")),
                string(me.get("last_name")), id == null ? null : "https://graph.facebook.com/" + id + "/picture?type=large");
    }
}

package net.neoforged.camelot.util.oauth;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A client for handling OAuth web authorization.
 */
public class OAuthClient {
    private final String authorizeUrl, tokenUrl;
    private final String clientId, clientSecret;
    private final Supplier<String> redirectUri;
    private final Set<String> scopes;
    private final HttpClient httpClient;

    public OAuthClient(String authorizeUrl, String tokenUrl, String clientId, String clientSecret, HttpClient httpClient, Object... scopes) {
        this(authorizeUrl, tokenUrl, clientId, clientSecret, () -> {
            throw new UnsupportedOperationException("Client has no redirect URI");
        }, httpClient, scopes);
    }

    public OAuthClient(String authorizeUrl, String tokenUrl, String clientId, String clientSecret, Supplier<String> redirectUri, HttpClient httpClient, Object... scopes) {
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.scopes = Arrays.stream(scopes).map(Object::toString).collect(Collectors.toUnmodifiableSet());
        this.httpClient = httpClient;
    }

    public OAuthClient fork(Supplier<String> redirectUri) {
        return new OAuthClient(authorizeUrl, tokenUrl, clientId, clientSecret, redirectUri, httpClient, scopes.toArray());
    }

    public TokenResponse getToken(String code) throws IOException, InterruptedException {
        return getToken(code, this.redirectUri.get());
    }

    public TokenResponse getToken(String code, String redirectUri) throws IOException, InterruptedException {
        final Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", redirectUri);
        params.put("scope", String.join(" ", scopes));
        final Instant requestedAt = Instant.now();
        final JSONObject response = httpClient.send(HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(params.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"))))
                .build(), jsonObject()).body();
        final String token = response.getString("access_token");
        final long expiresIn = response.getLong("expires_in");
        return new TokenResponse(token, requestedAt.plusSeconds(expiresIn));
    }

    public String getAuthorizationUrl(String state) {
        final String url = authorizeUrl + "?client_id=" + clientId + "&redirect_uri=" + URLEncoder.encode(redirectUri.get(), StandardCharsets.UTF_8) + "&response_type=code&scope=" + String.join("%20", scopes);
        return state == null ? url : (url + "&state=" + state);
    }

    public String getAuthorizationUrl() {
        return getAuthorizationUrl(null);
    }

    public static HttpResponse.BodyHandler<JSONObject> jsonObject() {
        return responseInfo -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), str -> new JSONObject(str));
    }
}

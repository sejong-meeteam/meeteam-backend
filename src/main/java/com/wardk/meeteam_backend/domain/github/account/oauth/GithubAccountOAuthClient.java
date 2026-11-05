package com.wardk.meeteam_backend.domain.github.account.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GithubAccountOAuthClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final URI TOKEN_URI = URI.create("https://github.com/login/oauth/access_token");
    private static final URI USER_URI = URI.create("https://api.github.com/user");
    private final GithubAccountOAuthProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GithubAccountOAuthClient(GithubAccountOAuthProperties properties, ObjectMapper objectMapper) {
        this(properties, HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), objectMapper);
    }

    public GithubAccountOAuthClient(GithubAccountOAuthProperties properties, HttpClient httpClient,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public String authorizeUrl(String signedState, String codeChallenge) {
        properties.requireEnabled();
        if (!safeValue(signedState, 4096) || codeChallenge == null
                || !codeChallenge.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getCallbackUri())
                .queryParam("state", signedState)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUriString();
    }

    public Identity identify(String code, String codeVerifier) {
        properties.requireEnabled();
        if (!safeValue(code, 2048) || codeVerifier == null
                || !codeVerifier.matches("[A-Za-z0-9._~-]{43,128}")) throw invalid();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", properties.getClientId());
        form.put("client_secret", properties.getClientSecret());
        form.put("redirect_uri", properties.getCallbackUri());
        form.put("code", code);
        form.put("code_verifier", codeVerifier);
        String body = form.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        try {
            HttpRequest tokenRequest = HttpRequest.newBuilder(TOKEN_URI).timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "MeeTeam-GitHub-Account")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            JsonNode token = sendJson(tokenRequest);
            String accessToken = text(token, "access_token");
            if (token.has("error") || !safeValue(accessToken, 4096)
                    || !accessToken.matches("[A-Za-z0-9._~+/-]+=*")
                    || !"bearer".equalsIgnoreCase(text(token, "token_type"))) throw upstream();
            // Tokens (including any refresh token) stay local to this exchange and are never persisted.
            HttpRequest userRequest = HttpRequest.newBuilder(USER_URI).timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "MeeTeam-GitHub-Account").GET().build();
            JsonNode user = sendJson(userRequest);
            JsonNode id = user.get("id");
            String login = text(user, "login");
            if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0
                    || login == null || login.length() > 39
                    || !login.matches("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*")) throw upstream();
            String avatar = text(user, "avatar_url");
            if (user.hasNonNull("avatar_url") && avatar == null) throw upstream();
            if (avatar == null || avatar.isEmpty()) avatar = "";
            else if (!safeAvatar(avatar)) throw upstream();
            return new Identity(id.longValue(), login, avatar);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw upstream();
        } catch (IOException | RuntimeException ignored) {
            throw upstream();
        }
    }

    private JsonNode sendJson(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null || response.body().length() > 1_048_576) {
            throw upstream();
        }
        JsonNode json = objectMapper.readTree(response.body());
        if (json == null || !json.isObject()) throw upstream();
        return json;
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static boolean safeValue(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean safeAvatar(String value) {
        try {
            URI uri = URI.create(value);
            return value.length() <= 500 && "https".equalsIgnoreCase(uri.getScheme())
                    && "avatars.githubusercontent.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && !value.contains("\\") && !value.toLowerCase(java.util.Locale.ROOT)
                            .matches(".*%(?:0[0-9a-f]|1[0-9a-f]|7f|5c).*" );
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static CustomException invalid() {
        return new CustomException(ErrorCode.GITHUB_INVALID_REQUEST, "GitHub 연결 요청이 올바르지 않습니다.");
    }
    private static CustomException upstream() {
        return new CustomException(ErrorCode.GITHUB_UPSTREAM_ERROR, "GitHub 계정 정보를 확인할 수 없습니다.");
    }

    public record Identity(long githubUserId, String login, String avatarUrl) { }
}

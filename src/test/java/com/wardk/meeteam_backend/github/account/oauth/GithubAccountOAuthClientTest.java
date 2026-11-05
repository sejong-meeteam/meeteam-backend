package com.wardk.meeteam_backend.github.account.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthClient;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthProperties;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GithubAccountOAuthClientTest {
    private static final String VERIFIER = "v".repeat(43);
    private static final String TOKEN = "ghu_privateAccessToken";
    private HttpClient http;
    private GithubAccountOAuthClient client;
    private GithubAccountOAuthProperties properties;

    @BeforeEach void setUp() {
        http = mock(HttpClient.class);
        properties = GithubAccountOAuthPropertiesTest.configured();
        client = new GithubAccountOAuthClient(properties, http, new ObjectMapper());
    }

    @Test void authorizationUsesFixedEndpointAndPkce() {
        String url = client.authorizeUrl("signed-state", "c".repeat(43));
        assertThat(url).startsWith("https://github.com/login/oauth/authorize?")
                .contains("client_id=client-id", "state=signed-state", "code_challenge=" + "c".repeat(43), "code_challenge_method=S256")
                .doesNotContain("client_secret", "private-client-secret", "scope=");
    }

    @Test void successfulExchangeReturnsOnlyValidatedIdentityAndDiscardsTokens() throws Exception {
        mockResponses(response(200, "{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"bearer\",\"refresh_token\":\"privateRefreshToken\"}"),
                response(200, "{\"id\":123,\"login\":\"octo-cat\",\"avatar_url\":\"https://avatars.githubusercontent.com/u/123?v=4\"}"));
        GithubAccountOAuthClient.Identity identity = client.identify("code+&=", VERIFIER);
        assertThat(identity).isEqualTo(new GithubAccountOAuthClient.Identity(123, "octo-cat", "https://avatars.githubusercontent.com/u/123?v=4"));
        assertThat(identity.toString()).doesNotContain(TOKEN, "privateRefreshToken");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest exchange = captor.getAllValues().get(0);
        HttpRequest user = captor.getAllValues().get(1);
        assertThat(exchange.uri()).isEqualTo(URI.create("https://github.com/login/oauth/access_token"));
        assertThat(exchange.method()).isEqualTo("POST");
        assertThat(exchange.headers().firstValue("Accept")).contains("application/json");
        assertThat(readBody(exchange)).contains("client_id=client-id", "client_secret=private-client-secret", "code=code%2B%26%3D", "code_verifier=" + VERIFIER,
                "redirect_uri=https%3A%2F%2Fmeeteam.example%2Fapi%2Fv1%2Fgithub%2Foauth%2Fcallback");
        assertThat(user.uri()).isEqualTo(URI.create("https://api.github.com/user"));
        assertThat(user.method()).isEqualTo("GET");
        assertThat(user.headers().firstValue("Authorization")).contains("Bearer " + TOKEN);
        assertThat(user.headers().firstValue("X-GitHub-Api-Version")).contains("2022-11-28");
        assertThat(user.headers().firstValue("User-Agent")).isPresent();
        assertThat(user.uri().toString()).doesNotContain(TOKEN);
        assertThat(exchange.timeout()).contains(Duration.ofSeconds(10));
        assertThat(user.timeout()).contains(Duration.ofSeconds(10));
    }

    @Test void productionClientNeverFollowsRedirectsAndHasBoundedConnectTimeout() throws Exception {
        GithubAccountOAuthClient production = new GithubAccountOAuthClient(properties, new ObjectMapper());
        var field = GithubAccountOAuthClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        HttpClient real = (HttpClient) field.get(production);
        assertThat(real.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(real.connectTimeout()).contains(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "not json privateRefreshToken", "{\"error\":\"bad_verification_code\",\"error_description\":\"privateRefreshToken\"}",
            "{\"access_token\":\"token\"}", "{\"access_token\":123,\"token_type\":\"bearer\"}", "{\"access_token\":\"bad token\",\"token_type\":\"bearer\"}"})
    void malformedTokenResponseFailsWithoutPayloadLeak(String body) throws Exception {
        mockResponses(response(200, body));
        assertUpstream();
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":0,\"login\":\"octocat\"}", "{\"id\":-1,\"login\":\"octocat\"}",
            "{\"id\":\"123\",\"login\":\"octocat\"}", "{\"id\":1.5,\"login\":\"octocat\"}",
            "{\"id\":9223372036854775808,\"login\":\"octocat\"}", "{\"id\":1,\"login\":\"-bad\"}",
            "{\"id\":1,\"login\":\"bad--login\"}", "{\"id\":1,\"login\":\"한글\"}",
            "{\"id\":1,\"login\":\"ok\",\"avatar_url\":\"https://avatars.githubusercontent.com.evil.example/a\"}",
            "{\"id\":1,\"login\":\"ok\",\"avatar_url\":\"http://avatars.githubusercontent.com/a\"}",
            "{\"id\":1,\"login\":\"ok\",\"avatar_url\":123}"})
    void rejectsMalformedIdentityAndUntrustedAvatar(String user) throws Exception {
        mockResponses(validToken(), response(200, user));
        assertUpstream();
    }

    @Test void rejectsAvatarExceedingDatabaseBound() throws Exception {
        String avatar = "https://avatars.githubusercontent.com/";
        avatar += "a".repeat(501 - avatar.length());
        mockResponses(validToken(), response(200, "{\"id\":1,\"login\":\"octocat\",\"avatar_url\":\"" + avatar + "\"}"));
        assertUpstream();
    }

    @Test void acceptsSignedStateUpToStateServiceLimit() {
        assertThat(client.authorizeUrl("s".repeat(4096), "c".repeat(43))).contains("state=" + "s".repeat(4096));
        assertThatThrownBy(() -> client.authorizeUrl("s".repeat(4097), "c".repeat(43)))
                .isInstanceOf(CustomException.class);
    }

    @Test void missingAvatarUsesEmptyString() throws Exception {
        mockResponses(validToken(), response(200, "{\"id\":1,\"login\":\"octocat\"}"));
        assertThat(client.identify("code", VERIFIER).avatarUrl()).isEmpty();
    }

    @Test void rejectsOversizedLogin() throws Exception {
        mockResponses(validToken(), response(200, "{\"id\":1,\"login\":\"" + "x".repeat(40) + "\"}"));
        assertUpstream();
    }

    @ParameterizedTest @ValueSource(ints = {301, 302, 400, 401, 429, 500})
    void tokenHttpFailuresAndRedirectsAreNotFollowed(int status) throws Exception {
        mockResponses(response(status, "privateRefreshToken"));
        assertUpstream();
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test void userHttpFailureIsSanitized() throws Exception {
        mockResponses(validToken(), response(503, TOKEN));
        assertUpstream();
    }

    @Test void transportExceptionIsSanitizedWithoutCause() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("privateRefreshToken " + TOKEN));
        assertUpstream();
    }

    @Test void interruptIsRestoredWithoutLeakingCause() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("privateRefreshToken"));
        try {
            assertUpstream();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }

    @Test void invalidInputsAndDisabledConfigurationNeverCallGitHub() {
        assertThatThrownBy(() -> client.identify("code", "short")).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> client.identify("code\r\n", VERIFIER)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> client.authorizeUrl("state", "short")).isInstanceOf(CustomException.class);
        properties.setEnabled(false);
        assertThatThrownBy(() -> client.identify("code", VERIFIER)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GITHUB_NOT_CONFIGURED));
        verifyNoInteractions(http);
    }

    private void assertUpstream() {
        assertThatThrownBy(() -> client.identify("code", VERIFIER)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GITHUB_UPSTREAM_ERROR);
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).doesNotContain(TOKEN, "privateRefreshToken", "private-client-secret");
        });
    }

    @SafeVarargs
    private void mockResponses(HttpResponse<String> first, HttpResponse<String>... rest) throws Exception {
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(first, rest);
    }

    private static HttpResponse<String> validToken() {
        return response(200, "{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"bearer\"}");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static String readBody(HttpRequest request) {
        List<Byte> bytes = new ArrayList<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { while (buffer.hasRemaining()) bytes.add(buffer.get()); }
            public void onError(Throwable throwable) { throw new AssertionError(throwable); }
            public void onComplete() { }
        });
        byte[] body = new byte[bytes.size()];
        for (int i = 0; i < body.length; i++) body[i] = bytes.get(i);
        return new String(body, StandardCharsets.UTF_8);
    }
}

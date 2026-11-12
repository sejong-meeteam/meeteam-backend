package com.wardk.meeteam_backend.global.auth.client;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** A pooled transport with strictly per-operation portal sessions. */
@Slf4j
@Component
public class SejongPortalClient implements AutoCloseable {
    private static final String PORTAL_URL = "https://portal.sejong.ac.kr";
    private static final String LOGIN_URL = PORTAL_URL + "/jsp/login/login_action.jsp";
    private static final String PROFILE_URL = PORTAL_URL + "/user/index.do";
    private static final String LOGIN_PAGE_URL = PORTAL_URL + "/jsp/login/loginSSL.jsp";
    private static final String SSO_URL = PORTAL_URL + "/comm/member/user/ssoLoginProc.do";
    private static final String SSO_RETURN = "portal.sejong.ac.kr/comm/member/user/ssoLoginProc.do";
    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final int MAX_CONNECTIONS = 5;
    private static final int IDLE_TIMEOUT_SECONDS = 120;
    private static final int CONNECTION_TTL_SECONDS = 300;
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int RESPONSE_TIMEOUT_SECONDS = 30;

    private final CloseableHttpClient httpClient;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final java.util.function.Consumer<String> diagnostic;

    public SejongPortalClient() {
        this((java.util.function.Consumer<String>) null);
    }

    /** Manual operator diagnostics receive fixed stage/status codes only, never response data. */
    SejongPortalClient(java.util.function.Consumer<String> diagnostic) {
        this.diagnostic = diagnostic;
        // Default TLS trust store and hostname verification are intentional.
        connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(MAX_CONNECTIONS)
                .setMaxConnPerRoute(MAX_CONNECTIONS)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                        .setSocketTimeout(Timeout.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                        .setTimeToLive(TimeValue.ofSeconds(CONNECTION_TTL_SECONDS))
                        .build())
                .build();
        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultCookieSpecRegistry(org.apache.hc.core5.http.config.RegistryBuilder
                        .<org.apache.hc.client5.http.cookie.CookieSpecFactory>create()
                        .register("portal-safe", context -> new PortalCookieSpec()).build())
                .disableRedirectHandling()
                .setDefaultRequestConfig(requestConfig())
                .evictIdleConnections(TimeValue.ofSeconds(IDLE_TIMEOUT_SECONDS))
                .evictExpiredConnections()
                .build();
    }

    /** Package-private seam for offline, mocked transport tests only. */
    SejongPortalClient(CloseableHttpClient httpClient) {
        this(httpClient, null);
    }

    SejongPortalClient(CloseableHttpClient httpClient, java.util.function.Consumer<String> diagnostic) {
        this.httpClient = httpClient;
        this.connectionManager = null;
        this.diagnostic = diagnostic;
    }

    private void trace(String stage) {
        if (diagnostic != null) {
            try { diagnostic.accept(stage); } catch (RuntimeException ignored) { }
        }
    }

    private String checkedHtml(ClassicHttpResponse response, HttpUriRequestBase request, String stage) throws IOException {
        trace(stage + "_HTTP_" + response.getCode());
        return readHtml(response, request);
    }

    private static RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setCookieSpec("portal-safe")
                .setConnectionRequestTimeout(Timeout.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .setResponseTimeout(Timeout.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                .build();
    }

    /** Existing-member login does not require a profile page. */
    public boolean authenticate(String studentId, String password) {
        withSession(studentId, password, false);
        return true;
    }

    /** Returns portal-verified identity only after a successful login in the same private session. */
    public SejongPortalProfile authenticateAndFetchProfile(String studentId, String password) {
        return withSession(studentId, password, true);
    }

    private SejongPortalProfile withSession(String studentId, String password, boolean fetchProfile) {
        BasicCookieStore cookies = new BasicCookieStore();
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookies);
        context.setRequestConfig(requestConfig());
        try {
            if (studentId == null || !studentId.matches("[0-9]{8}") || password == null || password.isEmpty()) {
                throw new CustomException(ErrorCode.SEJONG_LOGIN_FAILED);
            }
            // Initialize the private browser-style session before submitting credentials.
            trace("LOGIN_PAGE_REQUEST");
            HttpGet loginPage = new HttpGet(fetchProfile
                    ? LOGIN_PAGE_URL + "?rtUrl=" + URLEncoder.encode(SSO_RETURN, StandardCharsets.UTF_8)
                    : LOGIN_PAGE_URL);
            loginPage.setHeader("User-Agent", "Mozilla/5.0");
            httpClient.execute(loginPage, context, response -> checkedHtml(response, loginPage, "LOGIN_PAGE"));
            trace("LOGIN_REQUEST");
            PortalLoginResult.Status loginResult = PortalLoginResult.parse(performLogin(studentId, password, context, fetchProfile));
            trace("LOGIN_RESULT_" + loginResult.name());
            validateLoginResponse(loginResult, fetchProfile);
            trace(loginResult == PortalLoginResult.Status.OK ? "LOGIN_ACCEPTED" : "LOGIN_RESULT_UNCONFIRMED");
            if (!fetchProfile) return null;
            // Complete the public login page's same-host SSO-to-application handoff.
            // This is a fixed URL observed on the public login page, not a response URL.
            HttpGet sso = new HttpGet(SSO_URL);
            sso.setHeader("User-Agent", "Mozilla/5.0");
            sso.setHeader("Referer", LOGIN_PAGE_URL);
            trace("SSO_REQUEST");
            httpClient.execute(sso, context, response -> {
                trace("SSO_HTTP_" + response.getCode());
                // Cookies are processed before this callback. No body or supplied redirect
                // is needed: only the later private profile can establish authenticated identity.
                sso.cancel();
                if (response.getCode() == 200 || response.getCode() == 302 || response.getCode() == 303) return "";
                throw new IOException("Unexpected portal SSO status");
            });
            HttpGet request = new HttpGet(PROFILE_URL);
            request.setHeader("User-Agent", "Mozilla/5.0");
            request.setHeader("Referer", LOGIN_PAGE_URL);
            request.setHeader("Accept", "text/html");
            trace("PROFILE_REQUEST");
            String html = httpClient.execute(request, context, response -> checkedHtml(response, request, "PROFILE"));
            trace(html.contains("text01") ? "PROFILE_LAYOUT_PRESENT" : "PROFILE_LAYOUT_MISSING");
            trace("PROFILE_PARSE");
            SejongPortalProfile profile = SejongPortalProfileParser.parse(html, studentId);
            trace("PROFILE_VERIFIED");
            return profile;
        } catch (CustomException e) {
            trace("FAIL_" + e.getErrorCode().name());
            throw e;
        } catch (Exception e) {
            // Transport exceptions can contain request details. Never log them or portal response bodies.
            trace("FAIL_" + e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.SEJONG_PORTAL_ERROR);
        } finally {
            cookies.clear();
            context.setCookieStore(null);
            context.setUserToken(null);
        }
    }

    private String performLogin(String studentId, String password, HttpClientContext context, boolean fetchProfile) throws IOException {
        // The public browser form uses Y for an actual portal session; legacy verification uses N.
        String formData = "mainLogin=" + (fetchProfile ? "Y" : "N") + "&id=" + URLEncoder.encode(studentId, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + (fetchProfile ? "&rtUrl=" + URLEncoder.encode(SSO_RETURN, StandardCharsets.UTF_8) : "");
        HttpPost request = new HttpPost(LOGIN_URL);
        request.setHeader("User-Agent", "Mozilla/5.0");
        request.setHeader("Referer", LOGIN_PAGE_URL);
        request.setHeader("Origin", PORTAL_URL);
        request.setHeader("Accept", "text/html");
        request.setEntity(new StringEntity(formData, ContentType.APPLICATION_FORM_URLENCODED));
        return httpClient.execute(request, context, response -> {
            if (fetchProfile && (response.getCode() == 302 || response.getCode() == 303)) {
                // Do not follow the supplied URL or forward credentials. Only the fixed private
                // profile endpoint below may prove login, after response cookies are processed.
                trace("LOGIN_HTTP_" + response.getCode());
                request.cancel();
                return "";
            }
            return checkedHtml(response, request, "LOGIN");
        });
    }

    /** Abort rejected responses so a response-handler cleanup cannot drain an unbounded body. */
    private static String readHtml(ClassicHttpResponse response, HttpUriRequestBase request) throws IOException {
        try {
            return readHtml(response);
        } catch (IOException | RuntimeException e) {
            request.cancel();
            throw e;
        }
    }

    /** Reject redirects, binary responses and oversized bodies before parsing any success marker. */
    private static String readHtml(ClassicHttpResponse response) throws IOException {
        if (response.getCode() != 200) throw new IOException("Unexpected portal status");
        HttpEntity entity = response.getEntity();
        if (entity == null || entity.getContentLength() > MAX_BODY_BYTES) {
            throw new IOException("Invalid portal response");
        }
        ContentType contentType;
        try {
            contentType = ContentType.parse(entity.getContentType());
        } catch (RuntimeException e) {
            throw new IOException("Invalid portal content type");
        }
        if (contentType == null || !("text/html".equalsIgnoreCase(contentType.getMimeType())
                || "application/xhtml+xml".equalsIgnoreCase(contentType.getMimeType()))) {
            throw new IOException("Invalid portal content type");
        }
        Charset charset = contentType.getCharset() == null ? StandardCharsets.UTF_8 : contentType.getCharset();
        // The response-handler API owns stream cleanup. Do not close before the outer guard
        // aborts rejected requests: closing a partially read HTTP stream can drain the remainder.
        InputStream stream = entity.getContent();
        byte[] bytes = stream.readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new IOException("Portal response too large");
        return charset.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }

    private void validateLoginResponse(PortalLoginResult.Status result, boolean requireProfileProof) {
        switch (result) {
            case INVALID_CREDENTIALS, AMBIGUOUS -> throw new CustomException(ErrorCode.SEJONG_LOGIN_FAILED);
            case RESET_REQUIRED -> throw new CustomException(ErrorCode.SEJONG_PASSWORD_RESET_REQUIRED);
            case ACCESS_DENIED -> throw new CustomException(ErrorCode.SEJONG_PORTAL_ERROR);
            case UNKNOWN -> {
                // Never accept an unknown result as login. New registration can proceed only
                // if the following private profile request proves the exact student identity.
                if (!requireProfileProof) throw new CustomException(ErrorCode.SEJONG_LOGIN_FAILED);
            }
            case OK -> { }
        }
    }

    /** Anonymous warm-up uses its own short-lived cookie context, never an authentication session. */
    public void warmUp() {
        BasicCookieStore cookies = new BasicCookieStore();
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookies);
        context.setRequestConfig(requestConfig());
        try {
            HttpGet request = new HttpGet(PORTAL_URL);
            httpClient.execute(request, context, response -> {
                readHtml(response, request);
                return null;
            });
        } catch (Exception ignored) {
            log.warn("Portal connection warm-up unavailable");
        } finally {
            cookies.clear();
            context.setCookieStore(null);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        try {
            httpClient.close();
        } catch (IOException ignored) {
            log.warn("Portal HTTP client shutdown failed");
        } finally {
            if (connectionManager != null) connectionManager.close();
        }
    }
}

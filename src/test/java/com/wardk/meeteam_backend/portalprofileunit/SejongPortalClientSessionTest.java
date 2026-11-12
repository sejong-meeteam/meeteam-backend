package com.wardk.meeteam_backend.global.auth.client;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SejongPortalClientSessionTest {
    private static final String ID = "24001234";

    @Test
    void sharesSessionWithinCallAndClearsItBetweenCalls() throws Exception {
        CloseableHttpClient transport = mock(CloseableHttpClient.class);
        List<HttpClientContext> contexts = new ArrayList<>();
        List<CookieStore> stores = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        when(transport.execute(any(ClassicHttpRequest.class), any(HttpContext.class), anyHandler()))
                .thenAnswer(invocation -> {
                    ClassicHttpRequest request = invocation.getArgument(0);
                    HttpClientContext context = invocation.getArgument(1);
                    HttpClientResponseHandler<String> handler = invocation.getArgument(2);
                    int index = calls.getAndIncrement();
                    assertEquals("https", request.getUri().getScheme());
                    assertEquals("portal.sejong.ac.kr", request.getUri().getHost());
                    assertFalse(context.getRequestConfig().isRedirectsEnabled());
                    if (index % 4 == 0) {
                        assertEquals("GET", request.getMethod());
                        assertEquals("/jsp/login/loginSSL.jsp", request.getUri().getPath());
                        assertEquals("rtUrl=portal.sejong.ac.kr%2Fcomm%2Fmember%2Fuser%2FssoLoginProc.do", request.getUri().getRawQuery());
                        assertTrue(context.getCookieStore().getCookies().isEmpty());
                        contexts.add(context);
                        stores.add(context.getCookieStore());
                        BasicClientCookie cookie = new BasicClientCookie("SESSION", "synthetic");
                        cookie.setDomain("portal.sejong.ac.kr");
                        context.getCookieStore().addCookie(cookie);
                        return handler.handleResponse(response(200, "<html>Login form</html>", ContentType.TEXT_HTML));
                    }
                    assertSame(contexts.get(index / 4), context);
                    assertEquals(1, context.getCookieStore().getCookies().size());
                    if (index % 4 == 1) {
                        assertEquals("POST", request.getMethod());
                        assertEquals("/jsp/login/login_action.jsp", request.getUri().getPath());
                        assertEquals("mainLogin=Y&id=" + ID + "&password=synthetic&rtUrl=portal.sejong.ac.kr%2Fcomm%2Fmember%2Fuser%2FssoLoginProc.do", EntityUtils.toString(request.getEntity()));
                        assertEquals("https://portal.sejong.ac.kr/jsp/login/loginSSL.jsp", request.getFirstHeader("Referer").getValue());
                        return handler.handleResponse(response(200, "var result = 'OK'", ContentType.TEXT_HTML));
                    }
                    if (index % 4 == 2) {
                        assertEquals("GET", request.getMethod());
                        assertEquals("/comm/member/user/ssoLoginProc.do", request.getUri().getPath());
                        return handler.handleResponse(response(200, "<html>SSO handoff</html>", ContentType.TEXT_HTML));
                    }
                    assertEquals("GET", request.getMethod());
                    assertEquals("/user/index.do", request.getUri().getPath());
                    assertSame(contexts.get(index / 4), context);
                    assertEquals(1, context.getCookieStore().getCookies().size());
                    return handler.handleResponse(response(200, SejongPortalProfileParserTest.card("홍길동", ID, "컴퓨터공학과"), ContentType.TEXT_HTML));
                });
        SejongPortalClient client = new SejongPortalClient(transport);
        assertEquals(ID, client.authenticateAndFetchProfile(ID, "synthetic").studentId());
        assertEquals(ID, client.authenticateAndFetchProfile(ID, "synthetic").studentId());
        assertEquals(8, calls.get());
        assertNotSame(contexts.get(0), contexts.get(1));
        assertNotSame(stores.get(0), stores.get(1));
        stores.forEach(store -> assertTrue(store.getCookies().isEmpty()));
        contexts.forEach(context -> assertNull(context.getCookieStore()));
    }

    @Test
    void existingMemberLoginDoesNotFetchProfile() throws Exception {
        CloseableHttpClient transport = mock(CloseableHttpClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(transport.execute(any(ClassicHttpRequest.class), any(HttpContext.class), anyHandler()))
                .thenAnswer(invocation -> {
                    int stage = calls.getAndIncrement();
                    ClassicHttpRequest request = invocation.getArgument(0);
                    if (stage == 1) assertEquals("mainLogin=N&id=" + ID + "&password=synthetic", EntityUtils.toString(request.getEntity()));
                    if (stage == 0) assertNull(request.getUri().getRawQuery());
                    assertEquals(stage == 0 ? "GET" : "POST", request.getMethod());
                    assertEquals(stage == 0 ? "/jsp/login/loginSSL.jsp" : "/jsp/login/login_action.jsp", request.getUri().getPath());
                    return SejongPortalClientSessionTest.<String>handler(invocation.getArgument(2))
                            .handleResponse(response(200, stage == 0 ? "<html>Login form</html>" : "var result = 'OK'", ContentType.TEXT_HTML));
                });
        assertTrue(new SejongPortalClient(transport).authenticate(ID, "synthetic"));
        verify(transport, times(2)).execute(any(ClassicHttpRequest.class), any(HttpContext.class), anyHandler());
    }

    @Test
    void loginFailuresNeverFetchProfileAndPreserveErrorCodes() throws Exception {
        assertFailure("erridpwd", ErrorCode.SEJONG_LOGIN_FAILED);
        assertFailure("pwdNeedChg", ErrorCode.SEJONG_PASSWORD_RESET_REQUIRED);
        assertFailure("noaccess", ErrorCode.SEJONG_PORTAL_ERROR);
        assertFailure("var result = 'OK'; result = 'erridpwd';", ErrorCode.SEJONG_LOGIN_FAILED);
    }

    private void assertFailure(String body, ErrorCode expected) throws Exception {
        CloseableHttpClient transport = mock(CloseableHttpClient.class);
        List<CookieStore> stores = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        when(transport.execute(any(ClassicHttpRequest.class), any(HttpContext.class), anyHandler()))
                .thenAnswer(invocation -> {
                    HttpClientContext context = invocation.getArgument(1);
                    stores.add(context.getCookieStore());
                    context.getCookieStore().addCookie(new BasicClientCookie("SESSION", "synthetic"));
                    return SejongPortalClientSessionTest.<String>handler(invocation.getArgument(2))
                            .handleResponse(response(200, calls.getAndIncrement() == 0 ? "<html>Login form</html>" : body, ContentType.TEXT_HTML));
                });
        CustomException failure = assertThrows(CustomException.class,
                () -> new SejongPortalClient(transport).authenticateAndFetchProfile(ID, "synthetic"));
        assertEquals(expected, failure.getErrorCode());
        assertTrue(stores.get(0).getCookies().isEmpty());
        verify(transport, times(2)).execute(any(ClassicHttpRequest.class), any(HttpContext.class), anyHandler());
    }

    @Test
    void profileHttpFailuresAndMalformedProfilesFailClosedAndClearCookies() throws Exception {
        for (BasicClassicHttpResponse profileResponse : List.of(
                response(302, "redirect", ContentType.TEXT_HTML),
                response(200, "{}", ContentType.APPLICATION_JSON),
                response(200, "x".repeat(1_048_577), ContentType.TEXT_HTML),
                response(200, "<html>Login required</html>", ContentType.TEXT_HTML),
                response(200, SejongPortalProfileParserTest.card("홍길동", "24009999", "학과"), ContentType.TEXT_HTML))) {
            CloseableHttpClient transport = mock(CloseableHttpClient.class);
            List<CookieStore> stores = new ArrayList<>();
            AtomicInteger calls = new AtomicInteger();
            when(transport.execute(any(ClassicHttpRequest.class), any(HttpContext.class), anyHandler()))
                    .thenAnswer(invocation -> {
                        HttpClientContext context = invocation.getArgument(1);
                        int stage = calls.getAndIncrement();
                        if (stage < 2) {
                            stores.add(context.getCookieStore());
                            context.getCookieStore().addCookie(new BasicClientCookie("SESSION", "synthetic"));
                            return SejongPortalClientSessionTest.<String>handler(invocation.getArgument(2))
                                    .handleResponse(response(200, stage == 0 ? "<html>Login form</html>" : "var result = 'OK'", ContentType.TEXT_HTML));
                        }
                        if (stage == 2) return SejongPortalClientSessionTest.<String>handler(invocation.getArgument(2))
                                .handleResponse(response(200, "<html>SSO handoff</html>", ContentType.TEXT_HTML));
                        return SejongPortalClientSessionTest.<String>handler(invocation.getArgument(2)).handleResponse(profileResponse);
                    });
            CustomException failure = assertThrows(CustomException.class,
                    () -> new SejongPortalClient(transport).authenticateAndFetchProfile(ID, "synthetic"));
            assertEquals(ErrorCode.SEJONG_PORTAL_ERROR, failure.getErrorCode());
            assertTrue(stores.get(0).getCookies().isEmpty());
        }
    }

    @Test
    void transportFailureIsSanitizedAndSessionIsCleared() throws Exception {
        CloseableHttpClient transport = mock(CloseableHttpClient.class);
        List<CookieStore> stores = new ArrayList<>();
        when(transport.execute(any(ClassicHttpRequest.class), any(HttpContext.class), anyHandler()))
                .thenAnswer(invocation -> {
                    HttpClientContext context = invocation.getArgument(1);
                    stores.add(context.getCookieStore());
                    context.getCookieStore().addCookie(new BasicClientCookie("SESSION", "synthetic"));
                    throw new IOException("sensitive transport details");
                });
        CustomException failure = assertThrows(CustomException.class,
                () -> new SejongPortalClient(transport).authenticateAndFetchProfile(ID, "synthetic"));
        assertEquals(ErrorCode.SEJONG_PORTAL_ERROR, failure.getErrorCode());
        assertFalse(failure.getMessage().contains("sensitive"));
        assertNull(failure.getCause());
        assertTrue(stores.get(0).getCookies().isEmpty());
    }

    private static BasicClassicHttpResponse response(int status, String body, ContentType contentType) {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(status);
        response.setEntity(new StringEntity(body, contentType.withCharset(java.nio.charset.StandardCharsets.UTF_8)));
        return response;
    }

    private static HttpClientResponseHandler<String> anyHandler() {
        return any();
    }

    @SuppressWarnings("unchecked")
    private static <T> HttpClientResponseHandler<T> handler(Object value) {
        return (HttpClientResponseHandler<T>) value;
    }
}

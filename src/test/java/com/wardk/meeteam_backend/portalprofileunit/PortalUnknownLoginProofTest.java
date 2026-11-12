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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortalUnknownLoginProofTest {
    private static final String ID = "24001234";

    @Test
    void unknownNeverAuthorizesLoginOnlyOrFetchesProfile() throws Exception {
        var scenario = new Scenario(SejongPortalProfileParserTest.card("홍길동", ID, "학과"), 200, false);
        var failure = assertThrows(CustomException.class, () -> scenario.client.authenticate(ID, "synthetic-only"));
        assertEquals(ErrorCode.SEJONG_LOGIN_FAILED, failure.getErrorCode());
        scenario.assertFinished(2);
    }

    @Test
    void unknownNeedsExactAuthenticatedProfileProof() throws Exception {
        var scenario = new Scenario(SejongPortalProfileParserTest.card("홍길동", ID, "학과"));
        assertEquals(new SejongPortalProfile(ID, "홍길동", "학과"), scenario.client.authenticateAndFetchProfile(ID, "synthetic-only"));
        scenario.assertFinished(4);
    }

    @Test
    void unknownRejectsMismatchedIdentityAndStaleLoginPage() throws Exception {
        for (String html : List.of(SejongPortalProfileParserTest.card("홍길동", "24009999", "학과"), "<html><form>Login required</form></html>")) {
            var scenario = new Scenario(html);
            var failure = assertThrows(CustomException.class, () -> scenario.client.authenticateAndFetchProfile(ID, "synthetic-only"));
            assertEquals(ErrorCode.SEJONG_PORTAL_ERROR, failure.getErrorCode());
            scenario.assertFinished(4);
        }
    }

    @Test
    void postRedirectNeverUsesExternalLocationAndOnlyFixedProfileCanProveIdentity() throws Exception {
        for (int status : new int[]{302, 303}) {
            var scenario = new Scenario(SejongPortalProfileParserTest.card("홍길동", ID, "학과"), status, true);
            assertEquals(ID, scenario.client.authenticateAndFetchProfile(ID, "synthetic-only").studentId());
            scenario.assertFinished(4);
        }
    }

    @Test
    void postRedirectCannotAuthorizeMismatchedOrMissingProfile() throws Exception {
        for (int status : new int[]{302, 303}) {
            for (String html : List.of(SejongPortalProfileParserTest.card("홍길동", "24009999", "학과"), "<html>Login required</html>")) {
                var scenario = new Scenario(html, status, true);
                var failure = assertThrows(CustomException.class, () -> scenario.client.authenticateAndFetchProfile(ID, "synthetic-only"));
                assertEquals(ErrorCode.SEJONG_PORTAL_ERROR, failure.getErrorCode());
                scenario.assertFinished(4);
            }
        }
    }

    @Test
    void ssoRedirectDoesNotFollowExternalLocationAndRequiresFixedProfileProof() throws Exception {
        for (int status : new int[]{302, 303}) {
            var success = new Scenario(SejongPortalProfileParserTest.card("홍길동", ID, "학과"), 200, true, status);
            assertEquals(ID, success.client.authenticateAndFetchProfile(ID, "synthetic-only").studentId());
            success.assertFinished(4);
            for (String html : List.of(SejongPortalProfileParserTest.card("홍길동", "24009999", "학과"), "<html>Login required</html>")) {
                var failure = new Scenario(html, 200, true, status);
                assertEquals(ErrorCode.SEJONG_PORTAL_ERROR, assertThrows(CustomException.class,
                        () -> failure.client.authenticateAndFetchProfile(ID, "synthetic-only")).getErrorCode());
                failure.assertFinished(4);
            }
        }
    }

    @Test
    void ssoClientErrorsBlockProfileFetchAndClearSession() throws Exception {
        for (int status : new int[]{400, 401, 403, 404, 429}) {
            var scenario = new Scenario(SejongPortalProfileParserTest.card("홍길동", ID, "학과"), 200, true, status);
            assertEquals(ErrorCode.SEJONG_PORTAL_ERROR, assertThrows(CustomException.class,
                    () -> scenario.client.authenticateAndFetchProfile(ID, "synthetic-only")).getErrorCode());
            scenario.assertFinished(3);
        }
    }

    private static final class Scenario {
        final AtomicInteger calls = new AtomicInteger();
        final List<HttpClientContext> contexts = new ArrayList<>();
        final List<CookieStore> stores = new ArrayList<>();
        final SejongPortalClient client;

        Scenario(String profileHtml) throws Exception {
            this(profileHtml, 200, true);
        }

        Scenario(String profileHtml, int loginStatus, boolean fetchProfile) throws Exception {
            this(profileHtml, loginStatus, fetchProfile, 200);
        }

        Scenario(String profileHtml, int loginStatus, boolean fetchProfile, int ssoStatus) throws Exception {
            var transport = mock(CloseableHttpClient.class);
            when(transport.execute(any(ClassicHttpRequest.class), any(HttpContext.class), PortalUnknownLoginProofTest.<String>anyHandler()))
                    .thenAnswer(invocation -> {
                        ClassicHttpRequest request = invocation.getArgument(0);
                        HttpClientContext context = invocation.getArgument(1);
                        HttpClientResponseHandler<String> handler = invocation.getArgument(2);
                        int stage = calls.getAndIncrement();
                        contexts.add(context);
                        assertEquals("https", request.getUri().getScheme());
                        assertEquals("portal.sejong.ac.kr", request.getUri().getHost());
                        assertFalse(context.getRequestConfig().isRedirectsEnabled());
                        assertEquals("Mozilla/5.0", request.getFirstHeader("User-Agent").getValue());
                        if (stage >= 2) {
                            assertNull(request.getUri().getRawQuery());
                            assertEquals("https://portal.sejong.ac.kr/jsp/login/loginSSL.jsp", request.getFirstHeader("Referer").getValue());
                        }
                        if (stage == 1) {
                            assertEquals("mainLogin=" + (fetchProfile ? "Y" : "N") + "&id=" + ID + "&password=synthetic-only" + (fetchProfile ? "&rtUrl=portal.sejong.ac.kr%2Fcomm%2Fmember%2Fuser%2FssoLoginProc.do" : ""), EntityUtils.toString(request.getEntity()));
                            assertEquals("https://portal.sejong.ac.kr", request.getFirstHeader("Origin").getValue());
                            assertEquals("https://portal.sejong.ac.kr/jsp/login/loginSSL.jsp", request.getFirstHeader("Referer").getValue());
                        }
                        if (stage == 0) {
                            assertEquals("GET", request.getMethod());
                            assertEquals("/jsp/login/loginSSL.jsp", request.getUri().getPath());
                            assertEquals(fetchProfile ? "rtUrl=portal.sejong.ac.kr%2Fcomm%2Fmember%2Fuser%2FssoLoginProc.do" : null, request.getUri().getRawQuery());
                            assertTrue(context.getCookieStore().getCookies().isEmpty());
                            stores.add(context.getCookieStore());
                            context.getCookieStore().addCookie(new BasicClientCookie("SYNTHETIC_SESSION", "synthetic-only"));
                        } else {
                            assertSame(contexts.get(0), context);
                            assertSame(stores.get(0), context.getCookieStore());
                            assertEquals(1, context.getCookieStore().getCookies().size());
                            assertEquals(stage == 1 ? "POST" : "GET", request.getMethod());
                            assertEquals(stage == 1 ? "/jsp/login/login_action.jsp" : stage == 2 ? "/comm/member/user/ssoLoginProc.do" : "/user/index.do", request.getUri().getPath());
                        }
                        var response = new BasicClassicHttpResponse(stage == 1 ? loginStatus : stage == 2 ? ssoStatus : 200);
                        if ((stage == 1 && loginStatus != 200) || (stage == 2 && (ssoStatus == 302 || ssoStatus == 303))) {
                            response.setHeader("Location", "https://synthetic-untrusted.example/never-follow?token=synthetic-only");
                        }
                        response.setEntity(new StringEntity(stage == 0 ? "<html>Login form</html>" : stage == 1 ? "<script>/* unrecognized portal result */</script>" : stage == 2 ? "<html>SSO handoff is not identity proof</html>" : profileHtml, ContentType.TEXT_HTML.withCharset(java.nio.charset.StandardCharsets.UTF_8)));
                        return handler.handleResponse(response);
                    });
            client = new SejongPortalClient(transport);
        }

        void assertFinished(int expectedCalls) {
            assertEquals(expectedCalls, calls.get());
            assertTrue(stores.get(0).getCookies().isEmpty());
            contexts.forEach(context -> assertNull(context.getCookieStore()));
        }
    }

    private static <T> HttpClientResponseHandler<T> anyHandler() { return any(); }
}

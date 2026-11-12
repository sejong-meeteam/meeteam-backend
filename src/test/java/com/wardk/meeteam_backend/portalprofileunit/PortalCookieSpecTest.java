package com.wardk.meeteam_backend.global.auth.client;

import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalCookieSpecTest {
    private final PortalCookieSpec spec = new PortalCookieSpec();
    private final CookieOrigin https = new CookieOrigin("portal.sejong.ac.kr", 443, "/jsp/login/loginSSL.jsp", true);

    @Test
    void malformedExpiresHour24IsRejectedWithoutThrowing() {
        var cookies = assertDoesNotThrow(() -> spec.parse(new BasicHeader("Set-Cookie",
                "SYNTHETIC_SESSION=synthetic-only; Path=/; Expires=Wed, 09 Jun 2038 24:00:00 GMT; Secure; HttpOnly"), https));
        assertTrue(cookies.isEmpty());
    }

    @Test
    void preservesValidAuthenticationCookie() throws Exception {
        var cookies = spec.parse(new BasicHeader("Set-Cookie",
                "SYNTHETIC_SESSION=synthetic-only; Domain=portal.sejong.ac.kr; Path=/; Secure; HttpOnly"), https);
        assertEquals(1, cookies.size());
        var cookie = cookies.get(0);
        assertEquals("SYNTHETIC_SESSION", cookie.getName());
        assertEquals("synthetic-only", cookie.getValue());
        assertTrue(cookie.isSecure());
        assertDoesNotThrow(() -> spec.validate(cookie, https));
        assertTrue(spec.match(cookie, new CookieOrigin("portal.sejong.ac.kr", 443, "/user/index.do", true)));
    }

    @Test
    void preservesUniversityParentDomainSsoCookie() {
        var cookies = spec.parse(new BasicHeader("Set-Cookie",
                "SYNTHETIC_SESSION=synthetic-only; Domain=.sejong.ac.kr; Path=/; Secure; HttpOnly"), https);
        assertEquals(1, cookies.size());
        assertTrue(spec.match(cookies.get(0), new CookieOrigin("portal.sejong.ac.kr", 443, "/user/index.do", true)));
    }

    @Test
    void rejectsUnrelatedDomainCookies() {
        var cookies = spec.parse(new BasicHeader("Set-Cookie",
                "SYNTHETIC_SESSION=synthetic-only; Domain=unrelated.example; Path=/; Secure"), https);
        assertTrue(cookies.isEmpty());
    }

    @Test
    void rejectsPublicSuffixDomainCookie() {
        var cookies = spec.parse(new BasicHeader("Set-Cookie",
                "SYNTHETIC_SESSION=synthetic-only; Domain=ac.kr; Path=/; Secure"), https);
        assertTrue(cookies.isEmpty());
    }

    @Test
    void secureCookieCannotMatchHttpOrUnrelatedHost() throws Exception {
        var cookie = spec.parse(new BasicHeader("Set-Cookie",
                "SYNTHETIC_SESSION=synthetic-only; Domain=portal.sejong.ac.kr; Path=/; Secure"), https).get(0);
        assertFalse(spec.match(cookie, new CookieOrigin("portal.sejong.ac.kr", 80, "/user/index.do", false)));
        assertFalse(spec.match(cookie, new CookieOrigin("unrelated.example", 443, "/user/index.do", true)));
    }
}

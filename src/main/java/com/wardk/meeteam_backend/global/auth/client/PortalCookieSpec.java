package com.wardk.meeteam_backend.global.auth.client;

import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.client5.http.impl.cookie.RFC6265LaxSpec;
import org.apache.hc.core5.http.Header;
import java.util.ArrayList;
import java.util.List;

/** Reject malformed cookies before HttpClient can include their values in warnings. */
final class PortalCookieSpec implements org.apache.hc.client5.http.cookie.CookieSpec {
    private final RFC6265LaxSpec delegate = new RFC6265LaxSpec();

    @Override
    public List<Cookie> parse(Header header, CookieOrigin origin) {
        try {
            List<Cookie> accepted = new ArrayList<>();
            for (Cookie cookie : delegate.parse(header, origin)) {
                try {
                    delegate.validate(cookie, origin);
                    String domain = cookie.getDomain();
                    if (domain != null && domain.startsWith(".")) domain = domain.substring(1);
                    // This dedicated client contacts only the portal. Permit its exact host
                    // and the university parent domain used by SSO, never broad suffixes.
                    if ("portal.sejong.ac.kr".equals(domain) || "sejong.ac.kr".equals(domain)) {
                        accepted.add(cookie);
                    }
                } catch (MalformedCookieException ignored) {
                    // Intentionally no logging of the cookie/header or exception text.
                }
            }
            return accepted;
        } catch (MalformedCookieException ignored) {
            return List.of();
        }
    }

    @Override public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
        delegate.validate(cookie, origin);
    }
    @Override public boolean match(Cookie cookie, CookieOrigin origin) {
        return delegate.match(cookie, origin);
    }
    @Override public List<Header> formatCookies(List<Cookie> cookies) {
        return delegate.formatCookies(cookies);
    }
}

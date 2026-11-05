package com.wardk.meeteam_backend.github.account;

import com.wardk.meeteam_backend.domain.github.account.*;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.global.auth.repository.TokenBlacklistRepository;
import com.wardk.meeteam_backend.global.auth.service.CustomUserDetailsService;
import com.wardk.meeteam_backend.global.config.*;
import com.wardk.meeteam_backend.global.exception.*;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.github.account.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.net.URI;
import java.util.List;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GithubAccountController.class)
@Import({SecurityConfig.class, SecurityUrls.class, RestAccessDeniedHandler.class,
        RestAuthenticationEntryPoint.class, GithubAccountSecurityTest.CorsConfig.class})
class GithubAccountSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired SecurityUrls securityUrls;
    @MockBean com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver images;
    @MockBean GithubAccountService accounts;
    @MockBean GithubAccountOAuthService oauth;
    @MockBean JwtUtil jwt;
    @MockBean CustomUserDetailsService users;
    @MockBean TokenBlacklistRepository blacklist;
    private static final String ACCOUNT = "/api/v1/members/me/github";
    private static final String CALLBACK = "/api/v1/github/oauth/callback";
    @TestConfiguration static class CorsConfig {
        @Bean CorsProperties corsProperties() {
            var p = new CorsProperties(); p.setAllowedOrigins(List.of("https://front.example.invalid"));
            p.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
            p.setAllowedHeaders(List.of("*")); p.setExposedHeaders(List.of()); return p;
        }
    }
    private Member authenticate() {
        var member = Member.createForTest("unit@example.invalid", "테스트");
        ReflectionTestUtils.setField(member, "id", 7L);
        when(jwt.getCategory("unit-token")).thenReturn(JwtUtil.ACCESS_CATEGORY);
        when(jwt.getMemberId("unit-token")).thenReturn(7L);
        when(jwt.getUsername("unit-token")).thenReturn(member.getEmail());
        when(users.loadUserByUsername(member.getEmail())).thenReturn(new CustomSecurityUserDetails(member));
        return member;
    }
    @ParameterizedTest @ValueSource(strings = {"/api/v1/members/me/github", "/api/v1/members/me/github/authorize-url"})
    void accountReadsRequireAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized()); verifyNoInteractions(accounts, oauth);
    }
    @Test void disconnectRequiresAuthentication() throws Exception {
        mvc.perform(delete(ACCOUNT)).andExpect(status().isUnauthorized()); verifyNoInteractions(accounts);
    }
    @Test void unlinkedResultIsExplicitNullAndNeverCached() throws Exception {
        authenticate();
        mvc.perform(get(ACCOUNT).header("Authorization", "Bearer unit-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result").value(nullValue())).andExpect(content().string(org.hamcrest.Matchers.containsString("\"result\":null")))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(accounts).get(7);
    }
    @Test void disconnectReturnsExplicitNull() throws Exception {
        authenticate();
        mvc.perform(delete(ACCOUNT).header("Authorization", "Bearer unit-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(nullValue()));
        verify(accounts).disconnect(7);
    }
    @Test void authorizationResponseMatchesFrontendShape() throws Exception {
        authenticate(); when(oauth.authorize(7, "/settings")).thenReturn("https://github.com/login/oauth/authorize?state=test");
        mvc.perform(get(ACCOUNT + "/authorize-url").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.authorizeUrl").value("https://github.com/login/oauth/authorize?state=test"));
    }
    @Test void onlyDeclaredGithubCallbacksAndWebhookArePublic() {
        assertThatPublic(CALLBACK, "GET", true);
        assertThatPublic(CALLBACK, "POST", false);
        assertThatPublic("/api/v1/github/app/setup", "GET", true);
        assertThatPublic("/api/v1/github/app/setup", "POST", false);
        assertThatPublic("/api/v1/github/webhooks", "POST", true);
        assertThatPublic("/api/v1/github/webhooks", "GET", false);
        assertThatPublic(ACCOUNT, "GET", false);
    }
    private void assertThatPublic(String path, String method, boolean expected) {
        org.assertj.core.api.Assertions.assertThat(securityUrls.isWhitelisted(path, method)).isEqualTo(expected);
    }
    @Test void anonymousCallbackStillReachesServiceForSafeFailureRedirect() throws Exception {
        when(oauth.callback("code", "state", null, null)).thenReturn(URI.create("https://front.example.invalid/settings?github=failed"));
        mvc.perform(get(CALLBACK).param("code", "code").param("state", "state"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("https://front.example.invalid/settings?github=failed"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Referrer-Policy", "no-referrer"));
        verify(oauth).callback("code", "state", null, null);
    }
    @Test void callbackBindsToSessionForwardedAsHttpOnlyCookie() throws Exception {
        authenticate(); when(oauth.callback("code", "state", null, 7L)).thenReturn(URI.create("https://front.example.invalid/settings?github=connected"));
        mvc.perform(get(CALLBACK).param("code", "code").param("state", "state").cookie(new Cookie(JwtUtil.ACCESS_COOKIE_NAME, "unit-token")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("https://front.example.invalid/settings?github=connected"));
        verify(oauth).callback("code", "state", null, 7L);
    }
    @Test void revokedTokenCannotBindCallbackToMember() throws Exception {
        authenticate(); when(jwt.getAuthVersion("unit-token")).thenReturn(99L);
        when(oauth.callback("code", "state", null, null)).thenReturn(URI.create("https://front.example.invalid/settings?github=failed"));
        mvc.perform(get(CALLBACK).param("code", "code").param("state", "state").cookie(new Cookie(JwtUtil.ACCESS_COOKIE_NAME, "unit-token")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("https://front.example.invalid/settings?github=failed"));
        verify(oauth).callback("code", "state", null, null);
    }
}

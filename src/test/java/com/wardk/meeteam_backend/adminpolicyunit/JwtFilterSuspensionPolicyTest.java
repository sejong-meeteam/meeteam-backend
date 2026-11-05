package com.wardk.meeteam_backend.adminpolicyunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.global.auth.filter.JwtFilter;
import com.wardk.meeteam_backend.global.auth.repository.TokenBlacklistRepository;
import com.wardk.meeteam_backend.global.auth.service.CustomUserDetailsService;
import com.wardk.meeteam_backend.global.config.SecurityUrls;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtFilterSuspensionPolicyTest {
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final CustomUserDetailsService users = mock(CustomUserDetailsService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final JwtFilter filter = new JwtFilter(jwt, mock(SecurityUrls.class), users, mock(TokenBlacklistRepository.class));
    private Member member;

    @BeforeEach
    void setup() {
        member = Member.createForTest("unit@example.invalid", "Unit");
        ReflectionTestUtils.setField(member, "id", 12L);
        when(jwt.getCategory("token")).thenReturn(JwtUtil.ACCESS_CATEGORY);
        when(jwt.getUsername("token")).thenReturn(member.getEmail());
        when(jwt.getMemberId("token")).thenReturn(12L);
        when(users.loadUserByUsername(member.getEmail())).thenReturn(new CustomSecurityUserDetails(member));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    private MockHttpServletResponse request() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/private");
        request.addHeader("Authorization", "Bearer token");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void activeMatchingLegacyVersionAuthenticates() throws Exception {
        assertThat(request().getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void suspendedMemberGets401EvenWhenDetailsServiceIsMocked() throws Exception {
        member.suspend(null, "policy", LocalDateTime.now());
        when(jwt.getAuthVersion("token")).thenReturn(member.getAuthVersion());
        assertThat(request().getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void oldTokenRemainsRevokedAfterUnsuspension() throws Exception {
        member.suspend(null, "policy", LocalDateTime.now());
        member.unsuspend();
        assertThat(request().getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void reusedEmailCannotAuthenticateNewIdentity() throws Exception {
        when(jwt.getMemberId("token")).thenReturn(99L);
        assertThat(request().getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() throws Exception {
        when(jwt.getCategory("token")).thenReturn(JwtUtil.REFRESH_CATEGORY);
        assertThat(request().getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }
}

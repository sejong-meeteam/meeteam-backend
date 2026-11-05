package com.wardk.meeteam_backend.adminpolicyunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberDetailsSuspensionPolicyTest {
    @Test
    void suspendedDetailsAreRejectedButExpiredSuspensionIsActive() {
        var members = mock(MemberRepository.class);
        var service = new CustomUserDetailsService(members);
        var member = Member.createForTest("unit@example.invalid", "Unit");
        when(members.findByEmailAndDeletedAtIsNull(member.getEmail())).thenReturn(Optional.of(member));
        member.suspend(null, "policy", LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        assertThatThrownBy(() -> service.loadUserByUsername(member.getEmail())).isInstanceOf(DisabledException.class);
        member.suspend(1, "expired", LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(2));
        assertThat(service.loadUserByUsername(member.getEmail()).getMember()).isSameAs(member);
    }
}

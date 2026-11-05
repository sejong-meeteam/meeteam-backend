package com.wardk.meeteam_backend.adminpolicyunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class MemberSuspensionPolicyTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 6, 12, 0);

    @Test
    void suspensionExpiresAtExactBoundaryWithoutRestoringOldTokenVersion() {
        Member member = Member.createForTest("unit@example.invalid", "Unit");
        member.suspend(7, "policy", now);
        assertThat(member.isSuspendedAt(now.plusDays(7).minusNanos(1))).isTrue();
        assertThat(member.isSuspendedAt(now.plusDays(7))).isFalse();
        assertThat(member.getSuspendedUntil()).isEqualTo(now.plusDays(7));
        assertThat(member.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    void permanentSuspensionAndExplicitUnsuspensionNeverReuseAuthVersion() {
        Member member = Member.createForTest("unit@example.invalid", "Unit");
        member.suspend(null, "policy", now);
        assertThat(member.isSuspendedAt(now.plusYears(50))).isTrue();
        member.unsuspend();
        assertThat(member.isSuspendedAt(now)).isFalse();
        assertThat(member.getSuspendedUntil()).isNull();
        assertThat(member.getSuspendReason()).isNull();
        assertThat(member.getAuthVersion()).isEqualTo(2L);
        member.suspend(1, "again", now);
        assertThat(member.getAuthVersion()).isEqualTo(3L);
    }

    @Test
    void loginTimestampIsRecordedExplicitly() {
        Member member = Member.createForTest("unit@example.invalid", "Unit");
        assertThat(member.getLastLoginAt()).isNull();
        member.recordLogin(now);
        assertThat(member.getLastLoginAt()).isEqualTo(now);
    }
}

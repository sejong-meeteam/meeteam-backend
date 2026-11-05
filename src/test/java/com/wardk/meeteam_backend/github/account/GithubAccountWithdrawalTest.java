package com.wardk.meeteam_backend.github.account;

import com.wardk.meeteam_backend.domain.github.account.MemberGithubAccountRepository;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GithubAccountWithdrawalTest {
    @Mock MemberRepository members;
    @Mock MemberGithubAccountRepository githubAccounts;
    @InjectMocks AuthService auth;
    @Test void selfWithdrawalLocksMemberThenRemovesGithubIdentityAndRevokesOldState() {
        var member = Member.createForTest("unit@example.invalid", "테스트");
        when(members.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        auth.withdraw(7L);
        var ordered = inOrder(members, githubAccounts);
        ordered.verify(members).findByIdForUpdate(7L);
        ordered.verify(githubAccounts).deleteByMemberId(7L);
        assertThat(member.isDeleted()).isTrue();
        assertThat(member.getAuthVersion()).isEqualTo(1);
    }
    @Test void cleanupFailureAbortsWithdrawal() {
        var member = Member.createForTest("unit@example.invalid", "테스트");
        when(members.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        doThrow(new IllegalStateException()).when(githubAccounts).deleteByMemberId(7L);
        assertThatThrownBy(() -> auth.withdraw(7L)).isInstanceOf(IllegalStateException.class);
        assertThat(member.isDeleted()).isFalse();
    }
}

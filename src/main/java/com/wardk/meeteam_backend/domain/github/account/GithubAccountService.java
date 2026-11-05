package com.wardk.meeteam_backend.domain.github.account;

import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthClient.Identity;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubOAuthStateService.Validated;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.github.account.GithubAccountResponses.Account;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class GithubAccountService {
    private final EntityManager em;
    private final MemberGithubAccountRepository accounts;
    public record LinkContext(long memberId, long authVersion, long linkVersion) {}

    public Account get(long memberId) {
        lockActive(memberId);
        return accounts.findById(memberId).map(Account::from).orElse(null);
    }
    public LinkContext context(long memberId) {
        Member member = lockActive(memberId);
        return new LinkContext(memberId, member.getAuthVersion(), member.getGithubLinkVersion());
    }
    public void disconnect(long memberId) {
        Member member = lockActive(memberId);
        // An already-disconnected account must also cancel all outstanding authorize URLs.
        member.advanceGithubLinkVersion();
        accounts.deleteByMemberId(memberId);
    }
    public void connect(Validated state, Identity identity) {
        Member member = lockActive(state.memberId());
        if (member.getAuthVersion() != state.authVersion() || member.getGithubLinkVersion() != state.linkVersion())
            throw new CustomException(ErrorCode.GITHUB_INVALID_STATE);
        accounts.findByGithubUserId(identity.githubUserId()).ifPresent(existing -> {
            if (!existing.getMemberId().equals(member.getId())) throw new CustomException(ErrorCode.GITHUB_ACCOUNT_TAKEN);
        });
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        var account = accounts.findById(member.getId()).orElse(null);
        if (account == null) {
            accounts.save(new MemberGithubAccount(member, identity.githubUserId(), identity.login(), identity.avatarUrl(), now));
        } else {
            account.update(identity.githubUserId(), identity.login(), identity.avatarUrl(), now);
        }
        member.advanceGithubLinkVersion();
        // The unique constraint guards races between different MeeTeam members as well.
        em.flush();
    }
    private Member lockActive(long memberId) {
        Member member = em.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE);
        if (member == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        em.refresh(member, LockModeType.PESSIMISTIC_WRITE);
        if (member.isDeleted() || member.isAdminWithdrawn() || member.isSuspendedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul"))))
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        return member;
    }
}

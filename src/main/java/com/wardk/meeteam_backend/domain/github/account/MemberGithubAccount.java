package com.wardk.meeteam_backend.domain.github.account;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_github_account", uniqueConstraints = @UniqueConstraint(name = "uk_member_github_user", columnNames = "github_user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberGithubAccount {
    @Id @Column(name = "member_id") private Long memberId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_github_account_member"))
    private Member member;
    @Column(name = "github_user_id", nullable = false) private long githubUserId;
    @Column(nullable = false, length = 39) private String login;
    @Column(name = "avatar_url", length = 500) private String avatarUrl;
    @Column(name = "connected_at", nullable = false) private LocalDateTime connectedAt;

    public MemberGithubAccount(Member member, long githubUserId, String login, String avatarUrl, LocalDateTime now) {
        this.member = member;
        update(githubUserId, login, avatarUrl, now);
    }
    public void update(long githubUserId, String login, String avatarUrl, LocalDateTime now) {
        if (this.connectedAt == null || this.githubUserId != githubUserId) this.connectedAt = now;
        this.githubUserId = githubUserId;
        this.login = login;
        this.avatarUrl = avatarUrl;
    }
}

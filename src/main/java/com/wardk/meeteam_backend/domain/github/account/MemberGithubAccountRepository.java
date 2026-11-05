package com.wardk.meeteam_backend.domain.github.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface MemberGithubAccountRepository extends JpaRepository<MemberGithubAccount, Long> {
    Optional<MemberGithubAccount> findByGithubUserId(long githubUserId);
    @Modifying
    @Query("delete from MemberGithubAccount a where a.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") long memberId);
}

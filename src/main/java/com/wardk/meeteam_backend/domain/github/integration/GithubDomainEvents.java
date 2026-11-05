package com.wardk.meeteam_backend.domain.github.integration;

public final class GithubDomainEvents {
    private GithubDomainEvents() {}
    public record MemberJoined(long projectId, long memberId) {}
    public record MemberRemoved(long projectId, long memberId, Long githubUserId, String githubLogin) {
        public MemberRemoved(long projectId,long memberId) { this(projectId,memberId,null,null); }
    }
    public record AccountLinked(long memberId) {}
    public record RepositoryLinked(long projectId) {}
    public record ProjectDeleted(long projectId) {}
}

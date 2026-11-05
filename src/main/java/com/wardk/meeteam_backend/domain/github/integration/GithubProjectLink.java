package com.wardk.meeteam_backend.domain.github.integration;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Project-scoped GitHub delegation. No access token or source-code content is stored. */
@Entity
@Table(name = "github_project_link", uniqueConstraints = @UniqueConstraint(name = "uk_github_link_repository", columnNames = "github_repository_id"))
@Getter @Setter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubProjectLink {
    @Id @Column(name = "project_id") private Long projectId;
    private Long installationId;
    @Column(nullable = false) private boolean installationActive;
    private Long installedByMemberId;
    private Long installedGithubUserId;
    private Instant installedAt;
    @Lob @Column(columnDefinition = "LONGTEXT", nullable = false) private String authorizedRepositoryIds = "[]";
    @Column(name = "github_repository_id") private Long githubRepositoryId;
    @Column(length = 200) private String fullName;
    @Column(length = 500) private String htmlUrl;
    @Column(length = 500) private String description;
    @Column(nullable = false) private boolean repositoryPrivate;
    @Column(length = 1024) private String defaultBranch;
    @Column(nullable = false) private int stars;
    @Column(nullable = false) private int forks;
    @Column(nullable = false) private boolean autoInviteOnApproval = true;
    @Column(nullable = false) private boolean autoRevokeOnRemoval = true;
    private Instant linkedAt;
    @Column(nullable = false) private long bindingVersion;
    @Version private long rowVersion;

    public GithubProjectLink(long projectId) { this.projectId = projectId; }
    public boolean hasRepository() { return installationActive && installationId != null && githubRepositoryId != null; }
    public void advanceBinding() { bindingVersion = Math.incrementExact(bindingVersion); }
    public void clearRepository() {
        githubRepositoryId = null; fullName = null; htmlUrl = null; description = null;
        repositoryPrivate = false; defaultBranch = null; linkedAt = null; stars = 0; forks = 0;
        autoInviteOnApproval = true; autoRevokeOnRemoval = true; advanceBinding();
    }
}

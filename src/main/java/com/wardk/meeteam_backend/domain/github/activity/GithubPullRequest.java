package com.wardk.meeteam_backend.domain.github.activity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="github_pull_request",uniqueConstraints=@UniqueConstraint(name="uk_github_pr_number",columnNames={"repository_id","number"})) @Getter @Setter @NoArgsConstructor
public class GithubPullRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="repository_id",nullable=false) private long repositoryId;
 @Column(nullable=false) private int number;
 private Long githubUserId;
 @Column(length=100) private String authorLogin;
 @Column(length=1024) private String title;
 @Column(length=1000) private String url;
 private boolean open;
 private boolean draft;
 @Column(length=32) private String reviewDecision;
 private Instant createdAt;
 private Instant updatedAt;
 private Instant mergedAt;
}

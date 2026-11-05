package com.wardk.meeteam_backend.domain.github.activity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="github_repository_snapshot") @Getter @Setter @NoArgsConstructor
public class GithubRepositorySnapshot {
 @Id private Long repositoryId;
 private Instant lastPushedAt;
 private Instant syncedAt;
 private int openIssueCount;
 private int openPullRequestCount;
 @Lob @Column(columnDefinition="LONGTEXT",nullable=false) private String weeklyCounts="[0,0,0,0,0,0,0,0]";
 @Lob @Column(columnDefinition="LONGTEXT",nullable=false) private String languages="[]";
 @Lob @Column(columnDefinition="LONGTEXT",nullable=false) private String contributors="[]";
 public GithubRepositorySnapshot(long id){repositoryId=id;}
}

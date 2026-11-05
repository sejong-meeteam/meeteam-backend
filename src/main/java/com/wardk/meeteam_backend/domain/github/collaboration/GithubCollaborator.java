package com.wardk.meeteam_backend.domain.github.collaboration;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="project_github_collaborator", uniqueConstraints=@UniqueConstraint(name="uk_github_collaborator_member",columnNames={"project_id","member_id"}))
@Getter @Setter @NoArgsConstructor
public class GithubCollaborator {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="project_id",nullable=false) private Long projectId;
 @Column(name="member_id",nullable=false) private Long memberId;
 private long githubUserId;
 @Column(length=39) private String login;
 private Long invitationId;
 @Column(nullable=false,length=24) private String status="NOT_INVITED";
 private Instant invitedAt;
 @Column(length=300) private String failureReason;
 private long bindingVersion;
 @Column(length=36) private String operationToken;
 private Instant leaseUntil;
 private Long lastCompletedWorkId;
 @Version private long rowVersion;
 public GithubCollaborator(long projectId,long memberId){this.projectId=projectId;this.memberId=memberId;}
}

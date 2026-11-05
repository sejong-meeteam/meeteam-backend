package com.wardk.meeteam_backend.domain.github.collaboration;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
/** Durable identity history intentionally has no member/account foreign key. */
@Entity @Table(name="github_collaborator_grant",uniqueConstraints=@UniqueConstraint(name="uk_github_grant_identity",columnNames={"project_id","member_id","binding_version","github_user_id"}))
@Getter @Setter @NoArgsConstructor
public class GithubCollaboratorGrant {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="project_id",nullable=false) private long projectId;
 @Column(name="member_id",nullable=false) private long memberId;
 @Column(name="binding_version",nullable=false) private long bindingVersion;
 @Column(name="github_user_id",nullable=false) private long githubUserId;
 private long installationId;
 private Long repositoryId;
 @Column(length=200,nullable=false) private String repository;
 @Column(length=39,nullable=false) private String login;
 private Long invitationId;
 @Column(length=24,nullable=false) private String status="NOT_INVITED";
 private Instant invitedAt;
 @Column(length=300) private String failureReason;
 private Long lastCompletedWorkId;
 @Version private long rowVersion;
 public GithubCollaboratorGrant(GithubCollaborationService.Snapshot s){projectId=s.projectId();memberId=s.memberId();bindingVersion=s.bindingVersion();githubUserId=s.githubUserId();installationId=s.installationId();repositoryId=s.repositoryId();repository=s.repository();login=s.login();invitationId=s.invitationId();}
}

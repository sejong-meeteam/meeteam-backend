package com.wardk.meeteam_backend.domain.github.collaboration;
import com.wardk.meeteam_backend.domain.github.integration.GithubDomainEvents.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component @RequiredArgsConstructor
public class GithubCollaborationListener {
 private final GithubCollaborationService service;
 @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT) public void joined(MemberJoined e){service.enqueueInvite(e.projectId(),e.memberId());}
 @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT) public void removed(MemberRemoved e){service.enqueueRemoval(e.projectId(),e.memberId(),e.githubUserId(),e.githubLogin());}
 @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT) public void linked(AccountLinked e){service.accountLinked(e.memberId());}
 @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT) public void repository(RepositoryLinked e){service.repositoryLinked(e.projectId());}
 @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT) public void deleted(ProjectDeleted e){service.projectDeleted(e.projectId());}
}

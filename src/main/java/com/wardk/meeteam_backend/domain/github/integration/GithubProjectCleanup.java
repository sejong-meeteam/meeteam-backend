package com.wardk.meeteam_backend.domain.github.integration;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component @RequiredArgsConstructor
public class GithubProjectCleanup {
    private final EntityManager em;
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void deleted(GithubDomainEvents.ProjectDeleted event){
        // Deletion/unlink deliberately preserves already-granted GitHub repository permissions.
        var link=em.find(GithubProjectLink.class,event.projectId());if(link!=null)em.remove(link);
    }
}

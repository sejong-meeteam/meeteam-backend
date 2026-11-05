package com.wardk.meeteam_backend.domain.github.activity;
import com.wardk.meeteam_backend.domain.github.integration.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
@Component @RequiredArgsConstructor @ConditionalOnProperty(name="integration.github.app.enabled",havingValue="true")
public class GithubActivityScheduler {
 private final EntityManager em;private final GithubWorkQueue queue;
 @Scheduled(fixedDelayString="${integration.github.app.sync-interval-ms:3600000}") @Transactional
 public void sync(){for(var l:em.createQuery("select l from GithubProjectLink l where l.installationActive=true and l.githubRepositoryId is not null",GithubProjectLink.class).getResultList())queue.enqueue("SYNC",l.getProjectId(),null,l.getBindingVersion(),"{}",null);}
}

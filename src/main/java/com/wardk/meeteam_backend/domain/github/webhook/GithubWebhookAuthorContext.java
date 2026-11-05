package com.wardk.meeteam_backend.domain.github.webhook;
import com.wardk.meeteam_backend.domain.github.integration.GithubProjectLink;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class GithubWebhookAuthorContext {
 private final EntityManager em;
 public GithubWebhookAuthorContext(EntityManager em){this.em=em;}
 public record Repository(String fullName,String defaultBranch){}
 @Transactional(readOnly=true) public Repository repository(long installation,long repo){
  var links=em.createQuery("select l from GithubProjectLink l where l.installationId=:installation and l.githubRepositoryId=:repo and l.installationActive=true",GithubProjectLink.class).setParameter("installation",installation).setParameter("repo",repo).getResultList();
  return links.isEmpty()?null:new Repository(links.get(0).getFullName(),links.get(0).getDefaultBranch());
 }
}

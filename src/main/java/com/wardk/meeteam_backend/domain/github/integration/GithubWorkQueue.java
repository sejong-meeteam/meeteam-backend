package com.wardk.meeteam_backend.domain.github.integration;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class GithubWorkQueue {
    private final EntityManager em;
    @Transactional
    public void enqueue(String kind,Long projectId,Long memberId,long bindingVersion,String payload,String dedupKey) {
        // Coalesce waiting snapshots, but retain a follow-up when another sync is already running.
        if("SYNC".equals(kind) && projectId!=null && em.createQuery(
                "select count(w) from GithubWork w where w.kind='SYNC' and w.projectId=:project and w.bindingVersion=:binding and w.status='PENDING'",Long.class)
                .setParameter("project",projectId).setParameter("binding",bindingVersion).getSingleResult()>0) return;
        if(dedupKey!=null && exists(dedupKey)) return;
        em.persist(new GithubWork(kind,projectId,memberId,bindingVersion,payload,dedupKey));
        em.flush();
    }
    @Transactional(readOnly=true)
    public boolean exists(String dedupKey) {
        return em.createQuery("select count(w) from GithubWork w where w.dedupKey=:key",Long.class)
                .setParameter("key",dedupKey).getSingleResult()>0;
    }
}

package com.wardk.meeteam_backend.domain.github.integration;

import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import com.wardk.meeteam_backend.domain.github.integration.GithubWorkHandler.Job;

@Service
@Transactional(propagation=Propagation.REQUIRES_NEW)
public class GithubWorkTransactions {
    private final EntityManager em;
    private final Clock clock;
    @Autowired public GithubWorkTransactions(EntityManager em) { this(em,Clock.systemUTC()); }
    GithubWorkTransactions(EntityManager em,Clock clock) { this.em=em;this.clock=clock; }

    public Job claim() {
        Instant now=clock.instant();
        List<GithubWork> rows=em.createQuery("select w from GithubWork w where (w.status='PENDING' and w.nextAttemptAt<=:now) or (w.status='RUNNING' and w.lockedUntil<=:now) order by w.id",GithubWork.class)
                .setParameter("now",now).setMaxResults(1).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if(rows.isEmpty()) return null;
        GithubWork work=rows.get(0);em.refresh(work,LockModeType.PESSIMISTIC_WRITE);
        if(!eligible(work,now)) return null;
        work.setStatus("RUNNING");work.setAttempts(Math.incrementExact(work.getAttempts()));
        work.setLockedUntil(now.plus(Duration.ofMinutes(15)));
        return new Job(work.getId(),work.getKind(),work.getProjectId(),work.getMemberId(),work.getBindingVersion(),work.getPayload(),work.getAttempts());
    }
    private boolean eligible(GithubWork work,Instant now) {
        return ("PENDING".equals(work.getStatus())&&!work.getNextAttemptAt().isAfter(now))
                ||("RUNNING".equals(work.getStatus())&&work.getLockedUntil()!=null&&!work.getLockedUntil().isAfter(now));
    }
    private GithubWork owned(Job job) {
        GithubWork work=em.find(GithubWork.class,job.id(),LockModeType.PESSIMISTIC_WRITE);
        if(work!=null) em.refresh(work,LockModeType.PESSIMISTIC_WRITE);
        return work!=null&&"RUNNING".equals(work.getStatus())&&work.getAttempts()==job.attempt()?work:null;
    }
    public void heartbeat(Job job) {
        GithubWork work=owned(job);
        if(work!=null) work.setLockedUntil(clock.instant().plus(Duration.ofMinutes(15)));
    }
    public void done(Job job) {
        GithubWork work=owned(job);
        if(work==null) return;
        work.setStatus("DONE");work.setLockedUntil(null);work.setFailureReason(null);
        // Keep the delivery dedup key, not a redundant copy of metadata indefinitely.
        work.setPayload("{}");
    }
    public void failed(Job job) {
        GithubWork work=owned(job);
        if(work==null) return;
        work.setStatus(work.getAttempts()>=5?"FAILED":"PENDING");work.setLockedUntil(null);
        work.setNextAttemptAt(clock.instant().plusSeconds(Math.min(3600L,30L*(1L<<Math.min(work.getAttempts(),7)))));
        work.setFailureReason("GitHub 작업을 완료하지 못했습니다. 다시 시도해 주세요.");
    }
}

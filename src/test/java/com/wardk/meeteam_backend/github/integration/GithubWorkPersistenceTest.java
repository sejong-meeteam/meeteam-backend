package com.wardk.meeteam_backend.domain.github.integration;

import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(excludeAutoConfiguration=org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
 properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes=GithubWorkPersistenceTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class GithubWorkPersistenceTest {
 @Configuration @EntityScan(basePackageClasses=GithubWork.class)
 @Import(GithubWorkQueue.class)
 static class Config {
  @Bean EntityManager entityManager(EntityManagerFactory factory){return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);}
  @Bean MutableClock clock(){return new MutableClock();}
  @Bean GithubWorkTransactions transactions(EntityManager em,MutableClock clock){return new GithubWorkTransactions(em,clock);}
  @Bean Probe handler(){return new Probe();}
  @Bean GithubWorkWorker worker(GithubWorkTransactions tx,Probe probe){return new GithubWorkWorker(tx,List.of(probe));}
 }
 static class MutableClock extends Clock {
  Instant now=Instant.now();public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}
 }
 static class Probe implements GithubWorkHandler {
  AtomicInteger calls=new AtomicInteger();boolean fail;
  public String kind(){return "PROBE";}
  public void handle(Job job){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();calls.incrementAndGet();if(fail)throw new IllegalStateException("sensitive-upstream-details");}
 }
 @Autowired EntityManager em;@Autowired GithubWorkQueue queue;@Autowired GithubWorkTransactions transactions;
 @Autowired GithubWorkWorker worker;@Autowired MutableClock clock;@Autowired Probe probe;@Autowired PlatformTransactionManager manager;
 <T>T tx(java.util.function.Supplier<T> action){return new TransactionTemplate(manager).execute(s->action.get());}
 @BeforeEach void reset(){tx(()->{em.createQuery("delete from GithubWork").executeUpdate();return null;});clock.now=Instant.now().plusSeconds(2);probe.calls.set(0);probe.fail=false;}
 void enqueue(){queue.enqueue("PROBE",1L,2L,3,"{\"metadata\":true}",null);}
 GithubWork only(){return tx(()->em.createQuery("select w from GithubWork w",GithubWork.class).getSingleResult());}
 @Test void commitIsRequiredBeforeBackgroundSeesJob()throws Exception{
  var executor=Executors.newSingleThreadExecutor();
  try{tx(()->{enqueue();try{assertThat(executor.submit(worker::runOnce).get(5,TimeUnit.SECONDS)).isFalse();}catch(Exception e){throw new RuntimeException(e);}return null;});
   assertThat(worker.runOnce()).isTrue();assertThat(probe.calls.get()).isEqualTo(1);assertThat(only().getStatus()).isEqualTo("DONE");
  }finally{executor.shutdownNow();}
 }
 @Test void rollbackDoesNotLeaveAnExternalWorkItem(){
  assertThatThrownBy(()->tx(()->{enqueue();throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);
  assertThat(worker.runOnce()).isFalse();assertThat(probe.calls.get()).isZero();
 }
 @Test void failedJobRetainsMetadataAndSanitizesErrorsThenRetries(){
  enqueue();probe.fail=true;assertThat(worker.runOnce()).isTrue();var row=only();
  assertThat(row.getStatus()).isEqualTo("PENDING");assertThat(row.getAttempts()).isEqualTo(1);
  assertThat(row.getPayload()).contains("metadata");assertThat(row.getFailureReason()).doesNotContain("sensitive");
  assertThat(worker.runOnce()).isFalse();clock.now=row.getNextAttemptAt().plusSeconds(1);probe.fail=false;
  assertThat(worker.runOnce()).isTrue();assertThat(only().getPayload()).isEqualTo("{}");
 }
 @Test void fiveFailuresBecomeVisibleFailedInsteadOfInfiniteBusyLoop(){
  enqueue();probe.fail=true;
  for(int i=0;i<5;i++){assertThat(worker.runOnce()).isTrue();clock.now=only().getNextAttemptAt().plusSeconds(1);}
  assertThat(only().getStatus()).isEqualTo("FAILED");assertThat(worker.runOnce()).isFalse();
 }
 @Test void staleWorkerCannotFinalizeAnotherLease(){
  enqueue();var old=transactions.claim();clock.now=clock.now.plus(Duration.ofMinutes(16));var current=transactions.claim();
  assertThat(current.attempt()).isEqualTo(old.attempt()+1);transactions.done(old);transactions.failed(old);
  assertThat(only().getStatus()).isEqualTo("RUNNING");transactions.done(current);assertThat(only().getStatus()).isEqualTo("DONE");
 }
 @Test void heartbeatKeepsLongRunningJobFromBeingReclaimed(){
  enqueue();var job=transactions.claim();clock.now=clock.now.plus(Duration.ofMinutes(10));transactions.heartbeat(job);
  clock.now=clock.now.plus(Duration.ofMinutes(10));assertThat(transactions.claim()).isNull();transactions.done(job);
 }
 @Test void parallelWorkersHaveOnlyOneClaim()throws Exception{
  enqueue();var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{var a=executor.submit(()->{start.await();return transactions.claim();});var b=executor.submit(()->{start.await();return transactions.claim();});
   start.countDown();var results=Arrays.asList(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS));assertThat(results.stream().filter(Objects::nonNull).count()).isEqualTo(1);
  }finally{executor.shutdownNow();}
 }
 @Test void waitingSyncsCoalesceButRunningSyncKeepsOneFollowUp(){
  queue.enqueue("SYNC",1L,null,3,"{}",null);queue.enqueue("SYNC",1L,null,3,"{}",null);
  assertThat(tx(()->em.createQuery("select count(w) from GithubWork w",Long.class).getSingleResult())).isEqualTo(1);
  var running=transactions.claim();queue.enqueue("SYNC",1L,null,3,"{}",null);queue.enqueue("SYNC",1L,null,3,"{}",null);
  assertThat(tx(()->em.createQuery("select count(w) from GithubWork w",Long.class).getSingleResult())).isEqualTo(2);transactions.done(running);
 }
 @Test void dedupKeyRemainsAfterSuccessWithoutPersistingPayload(){
  queue.enqueue("PROBE",1L,null,0,"{}","delivery:unit");assertThat(worker.runOnce()).isTrue();
  queue.enqueue("PROBE",1L,null,0,"{}","delivery:unit");assertThat(worker.runOnce()).isFalse();assertThat(probe.calls.get()).isEqualTo(1);
 }
}

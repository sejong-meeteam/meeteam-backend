package com.wardk.meeteam_backend.domain.github.integration;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Claims and finalizes in short separate transactions; remote calls do not hold the claim lock. */
@Service
public class GithubWorkWorker {
    private final GithubWorkTransactions transactions;
    private final Map<String,GithubWorkHandler> handlers;
    public GithubWorkWorker(GithubWorkTransactions transactions,List<GithubWorkHandler> handlers) {
        this.transactions=transactions;
        this.handlers=handlers.stream().collect(Collectors.toUnmodifiableMap(GithubWorkHandler::kind,Function.identity()));
    }
    public boolean runOnce() {
        var job=transactions.claim();if(job==null)return false;
        var heartbeat=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"github-work-lease");t.setDaemon(true);return t;});
        try {
            heartbeat.scheduleAtFixedRate(()->{try{transactions.heartbeat(job);}catch(RuntimeException ignored){}},60,60,TimeUnit.SECONDS);
            var handler=handlers.get(job.kind());
            if(handler==null) throw new IllegalStateException("Unknown GitHub work kind");
            handler.handle(job);transactions.done(job);
        }catch(RuntimeException ignored){
            // Never log remote exception messages, OAuth values, request bodies, or signatures.
            transactions.failed(job);
        }finally{heartbeat.shutdownNow();}
        return true;
    }
}

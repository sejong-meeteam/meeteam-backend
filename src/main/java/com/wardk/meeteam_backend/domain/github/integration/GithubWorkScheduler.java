package com.wardk.meeteam_backend.domain.github.integration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="integration.github.app",name="enabled",havingValue="true")
public class GithubWorkScheduler {
    private final GithubWorkWorker worker;
    @Scheduled(fixedDelayString="${integration.github.work-delay-ms:5000}",initialDelayString="${integration.github.work-initial-delay-ms:10000}")
    public void runBatch() {
        for(int i=0;i<10;i++) {try{if(!worker.runOnce())return;}catch(RuntimeException ignored){return;}}
    }
}

package com.wardk.meeteam_backend.domain.github.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ConditionalOnProperty(prefix="integration.github.app",name="enabled",havingValue="true")
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass=true)
@EnableTransactionManagement(proxyTargetClass=true,order=Ordered.LOWEST_PRECEDENCE-100)
public class GithubIntegrationConfiguration {
    @Bean public ThreadPoolTaskScheduler githubTaskScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("github-background-");scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(15);return scheduler;
    }
}

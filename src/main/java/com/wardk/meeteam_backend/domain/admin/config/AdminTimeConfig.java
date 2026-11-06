package com.wardk.meeteam_backend.domain.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AdminTimeConfig {
    @Bean("adminClock")
    public Clock adminClock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
}

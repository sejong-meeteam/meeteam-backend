package com.wardk.meeteam_backend.domain.github.activity;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration
public class GithubActivityConfiguration { @Bean("githubActivityClock") public Clock clock(){return Clock.systemUTC();} }

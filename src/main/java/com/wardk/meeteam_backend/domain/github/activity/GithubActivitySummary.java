package com.wardk.meeteam_backend.domain.github.activity;
import java.time.Instant;
public record GithubActivitySummary(String activityLevel, Instant lastPushedAt) {}

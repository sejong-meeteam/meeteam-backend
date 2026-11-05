package com.wardk.meeteam_backend.github.activity;
import com.wardk.meeteam_backend.domain.github.activity.GithubActivityRules;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class GithubActivityRulesTest {
 private final Instant now=Instant.parse("2026-11-05T03:00:00Z");
 @Test void activityBoundaries(){assertThat(GithubActivityRules.level(null,now)).isEqualTo("DORMANT");assertThat(GithubActivityRules.level(now.minus(Duration.ofDays(7)),now)).isEqualTo("ACTIVE");assertThat(GithubActivityRules.level(now.minus(Duration.ofDays(7)).minusNanos(1),now)).isEqualTo("MODERATE");assertThat(GithubActivityRules.level(now.minus(Duration.ofDays(30)),now)).isEqualTo("MODERATE");assertThat(GithubActivityRules.level(now.minus(Duration.ofDays(30)).minusNanos(1),now)).isEqualTo("DORMANT");}
 @Test void usesSeoulMondayNotGithubSunday(){var counts=GithubActivityRules.weeks(List.of(Instant.parse("2026-11-01T14:59:59Z"),Instant.parse("2026-11-01T15:00:00Z")),now);assertThat(counts).hasSize(8);assertThat(counts.get(6)).isEqualTo(1);assertThat(counts.get(7)).isEqualTo(1);}
 @Test void excludesFutureAndOldCommits(){assertThat(GithubActivityRules.weeks(List.of(now.plusSeconds(1),now.minus(Duration.ofDays(100))),now)).containsOnly(0);}
 @Test void languageRoundingAndOther(){var shares=GithubActivityRules.languages(Map.of("A",1L,"B",1L,"C",1L,"D",1L,"E",1L,"F",1L));assertThat(shares).hasSize(6);assertThat(shares.get(5).name()).isEqualTo("Other");assertThat(shares.stream().mapToDouble(GithubActivityRules.Language::percentage).sum()).isCloseTo(100,within(0.0001));assertThat(GithubActivityRules.languages(Map.of())).isEmpty();}
 @Test void staleExcludesDraftApprovedAndBoundary(){Instant old=now.minus(Duration.ofHours(73));assertThat(GithubActivityRules.stale(false,"CHANGES_REQUESTED",old,now)).isTrue();assertThat(GithubActivityRules.stale(true,"REVIEW_REQUIRED",old,now)).isFalse();assertThat(GithubActivityRules.stale(false,"APPROVED",old,now)).isFalse();assertThat(GithubActivityRules.stale(false,null,now.minus(Duration.ofHours(72)),now)).isFalse();}
}

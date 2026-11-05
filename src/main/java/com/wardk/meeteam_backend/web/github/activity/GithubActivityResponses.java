package com.wardk.meeteam_backend.web.github.activity;
import com.fasterxml.jackson.annotation.*;
import com.wardk.meeteam_backend.domain.github.activity.GithubActivityRules.Language;
import java.time.*;
import java.util.*;
public final class GithubActivityResponses {
 public record Repository(long id,String fullName,String htmlUrl,String description,@JsonProperty("isPrivate") boolean isPrivate,String defaultBranch,int stars,int forks){}
 public record Contributor(String login,String avatarUrl,int commits,@JsonInclude(JsonInclude.Include.ALWAYS) Long memberId,@JsonInclude(JsonInclude.Include.ALWAYS) String memberName){}
 @JsonInclude(JsonInclude.Include.ALWAYS)
 public record Activity(Repository repository,String activityLevel,Instant lastPushedAt,List<Integer> weeklyCommits,int openPullRequestCount,int openIssueCount,List<Language> languages,List<Contributor> contributors,Instant syncedAt){}
 public record PullRequest(int number,String title,String url,String authorLogin,@JsonInclude(JsonInclude.Include.ALWAYS) String authorMemberName,@JsonProperty("isDraft") boolean isDraft,String reviewState,Instant createdAt,Instant updatedAt,@JsonProperty("isStale") boolean isStale){}
 public record Contribution(long memberId,String name,@JsonInclude(JsonInclude.Include.ALWAYS) String githubLogin,long commits,long mergedPullRequests,long reviews){}
 public record Progress(LocalDate periodStart,LocalDate periodEnd,List<PullRequest> pullRequests,List<Contribution> contributions){}
 public record Summary(long commits,long mergedPullRequests,long openedIssues,long closedIssues){}
 public record Merged(int number,String title,String url,String authorLogin,@JsonInclude(JsonInclude.Include.ALWAYS) String authorMemberName,Instant mergedAt){}
 public record Closed(int number,String title,String url,Instant closedAt){}
 public record WeeklyReport(LocalDate weekStart,LocalDate weekEnd,Summary summary,List<Merged> mergedPullRequests,List<Closed> closedIssues,List<Contribution> contributions){}
}
